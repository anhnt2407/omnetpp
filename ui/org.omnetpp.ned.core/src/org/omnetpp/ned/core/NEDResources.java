package org.omnetpp.ned.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.omnetpp.common.markers.ProblemMarkerSynchronizer;
import org.omnetpp.ned.model.INEDElement;
import org.omnetpp.ned.model.NEDElementConstants;
import org.omnetpp.ned.model.NEDSourceRegion;
import org.omnetpp.ned.model.NEDTreeDifferenceUtils;
import org.omnetpp.ned.model.NEDTreeUtil;
import org.omnetpp.ned.model.ex.ChannelElementEx;
import org.omnetpp.ned.model.ex.CompoundModuleElementEx;
import org.omnetpp.ned.model.ex.ModuleInterfaceElementEx;
import org.omnetpp.ned.model.ex.NEDElementFactoryEx;
import org.omnetpp.ned.model.ex.NedFileElementEx;
import org.omnetpp.ned.model.interfaces.INEDTypeInfo;
import org.omnetpp.ned.model.interfaces.INEDTypeResolver;
import org.omnetpp.ned.model.interfaces.INedTypeElement;
import org.omnetpp.ned.model.notification.INEDChangeListener;
import org.omnetpp.ned.model.notification.NEDAttributeChangeEvent;
import org.omnetpp.ned.model.notification.NEDBeginModelChangeEvent;
import org.omnetpp.ned.model.notification.NEDChangeListenerList;
import org.omnetpp.ned.model.notification.NEDEndModelChangeEvent;
import org.omnetpp.ned.model.notification.NEDModelEvent;
import org.omnetpp.ned.model.notification.NEDStructuralChangeEvent;
import org.omnetpp.ned.model.pojo.ChannelElement;
import org.omnetpp.ned.model.pojo.ChannelInterfaceElement;
import org.omnetpp.ned.model.pojo.CompoundModuleElement;
import org.omnetpp.ned.model.pojo.ExtendsElement;
import org.omnetpp.ned.model.pojo.GateElement;
import org.omnetpp.ned.model.pojo.GatesElement;
import org.omnetpp.ned.model.pojo.ModuleInterfaceElement;
import org.omnetpp.ned.model.pojo.NEDElementTags;
import org.omnetpp.ned.model.pojo.NedFileElement;
import org.omnetpp.ned.model.pojo.ParamElement;
import org.omnetpp.ned.model.pojo.ParametersElement;
import org.omnetpp.ned.model.pojo.SimpleModuleElement;

/**
 * Parses all NED files in the workspace and makes them available for other
 * plugins for consistency checks among NED files etc.
 *
 * It listens to workspace resource changes and modifies it content based on
 * change notifications.
 *
 * @author andras
 */
public class NEDResources implements INEDTypeResolver, IResourceChangeListener {

    // filters for component access with getAllComponentsFilteredBy
    public static final IPredicate SIMPLE_MODULE_FILTER = new IPredicate() {
        public boolean matches(INEDTypeInfo component) {
            return component.getNEDElement() instanceof SimpleModuleElement;
        }
    };
    public static final IPredicate COMPOUND_MODULE_FILTER = new IPredicate() {
        public boolean matches(INEDTypeInfo component) {
            return component.getNEDElement() instanceof CompoundModuleElement;
        }
    };
    public static final IPredicate NETWORK_FILTER = new IPredicate() {
        public boolean matches(INEDTypeInfo component) {
            return component.getNEDElement() instanceof CompoundModuleElementEx &&
                   ((CompoundModuleElementEx)component.getNEDElement()).getIsNetwork();
        }
    };

    private static final String NED_EXTENSION = "ned";

    // listener list that listeners on all NED changes
    private NEDChangeListenerList nedComponentChangeListenerList = null;
    private NEDChangeListenerList nedModelChangeListenerList = null;
    private final INEDChangeListener nedModelChangeListener =
                        new INEDChangeListener() {
                            public void modelChanged(NEDModelEvent event) {
                                nedModelChanged(event);
                            }
                        };
                        
    // stores parsed contents of NED files
    private final HashMap<IFile, NedFileElementEx> nedFiles = new HashMap<IFile, NedFileElementEx>();

    private final HashMap<IFile, Integer> connectCount = new HashMap<IFile, Integer>();

    // table of toplevel components (points into nedFiles trees)
    private final HashMap<String, INEDTypeInfo> components = new HashMap<String, INEDTypeInfo>();

    // reserved (used) names (contains all names including duplicates)
    private final Set<String> reservedNames = new HashSet<String>();

    // tables of toplevel components, classified (points into nedFiles trees)
    private boolean needsRehash = false; // if tables below need to be rebuilt
    // We use this counter to increment whenever a rehash occurred. Checks can be made
    // to assert that the function is not called unnecessarily
    private int debugRehashCounter = 0;

    private final HashMap<String, INEDTypeInfo> modules = new HashMap<String, INEDTypeInfo>();
    private final HashMap<String, INEDTypeInfo> channels = new HashMap<String, INEDTypeInfo>();
    private final HashMap<String, INEDTypeInfo> moduleInterfaces = new HashMap<String, INEDTypeInfo>();
    private final HashMap<String, INEDTypeInfo> channelInterfaces = new HashMap<String, INEDTypeInfo>();

    private INEDTypeInfo basicChannelType = null;
    private INEDTypeInfo nullChannelType = null;
    private INEDTypeInfo bidirChannelType = null;
    private INEDTypeInfo unidirChannelType = null;
    private boolean nedModelChangeNotificationDisabled = false;


    /**
     * Constructor.
     */
    public NEDResources() {
        createBuiltInNEDTypes();
    }

    /**
     * Create channel and interface types that are predefined in NED.
     */
    // FIXME should use built-in NED text from nedxml lib!!!
    protected void createBuiltInNEDTypes() {
        // create built-in channel type cIdealChannel
        ChannelElementEx nullChannel = (ChannelElementEx) NEDElementFactoryEx.getInstance().createElement(NEDElementTags.NED_CHANNEL);
        nullChannel.setName("cIdealChannel");
        nullChannel.setIsWithcppclass(true);
        nullChannelType = new NEDTypeInfo(nullChannel, null, this);

        // create built-in channel type cBasicChannel
        ChannelElementEx basicChannel = (ChannelElementEx) NEDElementFactoryEx.getInstance().createElement(NEDElementTags.NED_CHANNEL);
        basicChannel.setName("cBasicChannel");
        basicChannel.setIsWithcppclass(true);
        ParametersElement params = (ParametersElement) NEDElementFactoryEx.getInstance().createElement(
                NEDElementTags.NED_PARAMETERS, basicChannel);
        params.appendChild(createImplicitChannelParameter("delay", NEDElementConstants.NED_PARTYPE_DOUBLE));
        params.appendChild(createImplicitChannelParameter("error", NEDElementConstants.NED_PARTYPE_DOUBLE));
        params.appendChild(createImplicitChannelParameter("datarate", NEDElementConstants.NED_PARTYPE_DOUBLE));
        basicChannelType = new NEDTypeInfo(basicChannel, null, this);

        //
        // create built-in interfaces that allow modules to be used as channels
        // interface IBidirectionalChannel { gates: inout a; inout b; }
        // interface IUnidirectionalChannel {gates: input i; output o; }
        //
        ModuleInterfaceElementEx bidirChannel = (ModuleInterfaceElementEx) NEDElementFactoryEx.getInstance().createElement(
                NEDElementTags.NED_MODULE_INTERFACE);
        bidirChannel.setName("IBidirectionalChannel");
        GatesElement gates = (GatesElement) NEDElementFactoryEx.getInstance().createElement(NEDElementTags.NED_GATES, bidirChannel);
        gates.appendChild(createGate("a", NEDElementConstants.NED_GATETYPE_INOUT));
        gates.appendChild(createGate("b", NEDElementConstants.NED_GATETYPE_INOUT));
        bidirChannelType = new NEDTypeInfo(bidirChannel, null, this);

        ModuleInterfaceElementEx unidirChannel = (ModuleInterfaceElementEx) NEDElementFactoryEx.getInstance().createElement(
                NEDElementTags.NED_MODULE_INTERFACE);
        unidirChannel.setName("IUnidirectionalChannel");
        GatesElement gates2 = (GatesElement) NEDElementFactoryEx.getInstance().createElement(NEDElementTags.NED_GATES,
                unidirChannel);
        gates2.appendChild(createGate("i", NEDElementConstants.NED_GATETYPE_INPUT));
        gates2.appendChild(createGate("o", NEDElementConstants.NED_GATETYPE_OUTPUT));
        unidirChannelType = new NEDTypeInfo(unidirChannel, null, this);
    }

    /* utility method */
    protected INEDElement createGate(String name, int type) {
        GateElement g = (GateElement) NEDElementFactoryEx.getInstance().createElement(NEDElementTags.NED_GATE);
        g.setName(name);
        g.setType(type);
        return g;
    }

    /* utility method */
    protected INEDElement createImplicitChannelParameter(String name, int type) {
        ParamElement param = (ParamElement) NEDElementFactoryEx.getInstance().createElement(NEDElementTags.NED_PARAM);
        param.setName(name);
        param.setType(type);
        param.setIsVolatile(false);
        param.setIsDefault(false);
        // TODO add default value of zero
        param.setSourceLocation("internal");
        return param;
    }

    public synchronized Set<IFile> getNEDFiles() {
        return nedFiles.keySet();
    }

    public synchronized NedFileElementEx getNEDFileModel(IFile file) {
    	Assert.isTrue(nedFiles.containsKey(file), "file is not a NED file, or not parsed yet");
		rehashIfNeeded();
        return nedFiles.get(file);
    }

    /**
     * Returns the textual (reformatted) content of the NED file, generated
     * from the model that belongs to the given file.
     */
    public synchronized String getNEDFileText(IFile file) {
        return NEDTreeUtil.generateNedSource(getNEDFileModel(file), true);
    }

    /**
     * NED text editors should call this when editor content changes.
     * Parses the given text, and synchronizes the stored NED model tree to it.
     * @param file - which file should be set
     * @param text - the textual content of the ned file
     */
    public synchronized void setNEDFileText(IFile file, String text) {
        INEDElement currentTree = getNEDFileModel(file);

        // parse
        ProblemMarkerSynchronizer markerSync = new ProblemMarkerSynchronizer(NEDMarkerErrorStore.NEDPROBLEM_MARKERID);
        markerSync.registerFile(file);
        NEDMarkerErrorStore errorStore = new NEDMarkerErrorStore(file, markerSync, NEDMarkerErrorStore.NEDPROBLEM_MARKERID);
        INEDElement targetTree = NEDTreeUtil.parseNedSource(text, errorStore, file.getLocation().toOSString());
        
        NEDTreeDifferenceUtils.Applier treeDifferenceApplier = new NEDTreeDifferenceUtils.Applier();
        NEDTreeDifferenceUtils.applyTreeDifferences(currentTree, targetTree, treeDifferenceApplier);

        if (treeDifferenceApplier.hasDifferences()) {
        	// push tree differences into the official tree
        	System.out.println("pushing text editor changes into NEDResources tree:\n  " + treeDifferenceApplier);
	        currentTree.fireModelChanged(new NEDBeginModelChangeEvent(currentTree));
	        treeDifferenceApplier.apply();
	        currentTree.fireModelChanged(new NEDEndModelChangeEvent(currentTree));

	        // perform marker synchronization in a background job, to avoid deadlocks
	        markerSync.runAsWorkspaceJob();
	        
	        // force rehash now, so that validation errors appear immediately
	        rehash();
        }
    }

    // TODO we should use the NEDElements (the errorMarkerIds collection) to signal an error
    // using the markers are problematic because the markers are added asynchronously in a background job
    public synchronized boolean hasError(IFile file) {
        try {
            return file.findMaxProblemSeverity(IMarker.PROBLEM, true, IResource.DEPTH_ZERO) >= IMarker.SEVERITY_ERROR;
        } catch (CoreException e) {
            // if resource does not exists or the project is closed (=no error)
            return false;
        }
    }


    public synchronized INEDTypeInfo getComponentAt(IFile file, int lineNumber) {
		rehashIfNeeded();
        for (INEDTypeInfo component : components.values()) {
            if (file.equals(component.getNEDFile())) {
                NEDSourceRegion region = component.getNEDElement().getSourceRegion();
                if (region != null && region.containsLine(lineNumber))
                    return component;
            }
        }
        return null;
    }

    public synchronized INEDElement getNEDElementAt(IFile file, int line, int column) {
        line++;  // IDocument is 0-based

        // find component and NEDElements under the cursor
        INEDTypeInfo c = getComponentAt(file, line);
        if (c!=null) {
            INEDElement[] nodes = c.getNEDElementsAt(line, column);
            if (nodes!=null && nodes.length>0)
                return nodes[nodes.length-1];
        }
        return null;
    }

    public synchronized Collection<INEDTypeInfo> getAllComponents() {
		rehashIfNeeded();
        return components.values();
    }

    public Collection<INEDTypeInfo> getAllComponentsFilteredBy(IPredicate f) {
        List<INEDTypeInfo> result = new ArrayList<INEDTypeInfo>();
        for (INEDTypeInfo comp : getAllComponents())
            if (f.matches(comp))
                result.add(comp);
        return result;
    }

    public Set<String> getAllComponentNamesFilteredBy(IPredicate f) {
        Set<String> result = new HashSet<String>();
        for (INEDTypeInfo comp : getAllComponents())
            if (f.matches(comp))
                result.add(comp.getName());
        return result;
    }

    public synchronized Collection<INEDTypeInfo> getModules() {
		rehashIfNeeded();
        return modules.values();
    }

    public synchronized Collection<INEDTypeInfo> getNetworks() {
    	return getAllComponentsFilteredBy(NETWORK_FILTER);
    }

    public synchronized Collection<INEDTypeInfo> getChannels() {
		rehashIfNeeded();
        return channels.values();
    }

    public synchronized Collection<INEDTypeInfo> getModuleInterfaces() {
		rehashIfNeeded();
        return moduleInterfaces.values();
    }

    public synchronized Collection<INEDTypeInfo> getChannelInterfaces() {
		rehashIfNeeded();
        return channelInterfaces.values();
    }

    public synchronized Set<String> getAllComponentNames() {
		rehashIfNeeded();
        return components.keySet();
    }

    public synchronized Set<String> getReservedComponentNames() {
		rehashIfNeeded();
        return reservedNames;
    }

    public synchronized Set<String> getModuleNames() {
		rehashIfNeeded();
        return modules.keySet();
    }

    public synchronized Set<String> getNetworkNames() {
    	return getAllComponentNamesFilteredBy(NETWORK_FILTER);
    }

    public synchronized Set<String> getChannelNames() {
		rehashIfNeeded();
        return channels.keySet();
    }

    public synchronized Set<String> getModuleInterfaceNames() {
		rehashIfNeeded();
        return moduleInterfaces.keySet();
    }

    public synchronized Set<String> getChannelInterfaceNames() {
		rehashIfNeeded();
        return channelInterfaces.keySet();
    }

    public synchronized INEDTypeInfo getComponent(String name) {
		rehashIfNeeded();
        return components.get(name);
    }

    public synchronized INEDTypeInfo wrapNEDElement(INedTypeElement componentNode) {
        return new NEDTypeInfo(componentNode, null, this);
    }

    /**
     * Determines if a resource is a NED file, that is, if it should be parsed.
     */
    public static boolean isNEDFile(IResource resource) {
        // TODO should only regard files within a folder designated as "source
        // folder" (persistent attribute!)
        return resource instanceof IFile && NED_EXTENSION.equalsIgnoreCase(((IFile) resource).getFileExtension());
    }

    /**
     * NED editors should call this when they get opened.
     */
    public synchronized void connect(IFile file) {
        readNEDFile(file);
        if (connectCount.containsKey(file))
            connectCount.put(file, connectCount.get(file) + 1);
        else {
            connectCount.put(file, 1);
        }
    }

    /**
     * NED editors should call this when they get closed.
     */
    public synchronized void disconnect(IFile file) {
        int count = connectCount.get(file); // must exist
        if (count <= 1) {
            // there's no open editor -- remove counter and re-read last saved
            // state from disk
            connectCount.remove(file);
            readNEDFile(file);
        }
        else {
            connectCount.put(file, count - 1);
        }
    }

    public synchronized void readNEDFile(IFile file) {
        ProblemMarkerSynchronizer sync = new ProblemMarkerSynchronizer();
        readNEDFile(file, sync);
        sync.runAsWorkspaceJob();
    }

    /**
     * Gets called from incremental builder.
     */
    public synchronized void readNEDFile(IFile file, ProblemMarkerSynchronizer markerSync) {
        // if this file is currently loaded in an editor, we don't read it from disk
        if (connectCount.containsKey(file))
            return;

        System.out.println("reading from disk: " + file.toString());

        // parse the NED file and put it into the hash table
        String fileName = file.getLocation().toOSString();
        NEDMarkerErrorStore errorStore = new NEDMarkerErrorStore(file, markerSync, NEDMarkerErrorStore.NEDPROBLEM_MARKERID);
        NedFileElementEx tree = NEDTreeUtil.loadNedSource(fileName, errorStore);
        Assert.isNotNull(tree);
        
        storeNEDFileModel(file, tree);
    }

    /**
     * Forget a NED file, and throws out all cached info.
     */
    public synchronized void forgetNEDFile(IFile file) {
        if (nedFiles.containsKey(file)) {
            // remove our model change from the file
            nedFiles.get(file).removeNEDChangeListener(nedModelChangeListener);
            nedFiles.remove(file);
            invalidate();
        }
    }
    
    // store NED file contents
    private synchronized void storeNEDFileModel(IFile file, NedFileElementEx tree) {
        Assert.isTrue(!connectCount.containsKey(file), "cannot replace the tree while an editor is open");

        NedFileElementEx oldTree = nedFiles.get(file);
        // if the new tree has changed, we have to rehash everything
        if (oldTree == null || !NEDTreeUtil.isNEDTreeEqual(oldTree, tree)) {
            invalidate();
            nedFiles.put(file, tree);
            // add ourselves to the tree root as a listener
            tree.addNEDChangeListener(nedModelChangeListener);
            // remove ourselves from the old tree which is no longer used
            if (oldTree != null)
                oldTree.removeNEDChangeListener(nedModelChangeListener);
            // fire a ned change notification (new tree added)
            nedModelChanged(new NEDStructuralChangeEvent(tree, tree, NEDStructuralChangeEvent.Type.INSERTION, tree, tree));
        }
    }

	private void rehash() {
		invalidate();
		rehashIfNeeded();
	}

    /**
     * Rebuild hash tables after NED resource change. Note: some errors such as
     * duplicate names only get detected when this gets run!
     */
    public synchronized void rehashIfNeeded() {

        if (!needsRehash)
            return;

        long startMillis = System.currentTimeMillis();

        needsRehash = false;
        debugRehashCounter++;

        Set<String> duplicates = new HashSet<String>();
        components.clear();
        channels.clear();
        channelInterfaces.clear();
        modules.clear();
        moduleInterfaces.clear();
        reservedNames.clear();

        components.put(nullChannelType.getName(), nullChannelType);
        components.put(basicChannelType.getName(), basicChannelType);
        components.put(bidirChannelType.getName(), bidirChannelType);
        components.put(unidirChannelType.getName(), unidirChannelType);

        ProblemMarkerSynchronizer markerSync = new ProblemMarkerSynchronizer(NEDMarkerErrorStore.NEDCONSISTENCYPROBLEM_MARKERID);

        // find NED types in each file, and register them
        for (IFile file : nedFiles.keySet()) {
        	markerSync.registerFile(file);

        	// collect types (including inner types) from the NED file, and process them one by one
        	Map<String, INedTypeElement> types = collectTypesFrom(nedFiles.get(file));
        	for (String name : types.keySet()) {
        		INedTypeElement node = types.get(name);

        		// create type info object for EVERY type. We won't store them for duplicate types,
        		// but they'll still be available via INedTypeInfo.getNedTypeInfo().
        		INEDTypeInfo typeInfo = new NEDTypeInfo(node, file, this);

        		if (components.containsKey(name)) {
        			// it is a duplicate: issue warning
        			IFile otherFile = components.get(name).getNEDFile();
        			INEDElement otherElement = components.get(name).getNEDElement();
                	NEDMarkerErrorStore errorStore = new NEDMarkerErrorStore(file, markerSync);
        			if (otherFile == null) {
        				errorStore.addError(node, node.getReadableTagName() + " '" + name + "' is a built-in type and cannot be redefined");
        			}
        			else {
        				// add it to the duplicate set so we can remove them at the end
        				duplicates.add(name);

        				// add error message to both files
        				final String messageHalf = node.getReadableTagName() + " '" + name + "' already defined in ";
						errorStore.addError(node, messageHalf + otherFile.getFullPath().toString());
        	        	NEDMarkerErrorStore otherErrorStore = new NEDMarkerErrorStore(otherFile, markerSync);
        				otherErrorStore.addError(otherElement, messageHalf + file.getFullPath().toString());
        			}
        		}
        		else {
        			// normal case: not duplicate. Add the type info to our tables.
        			HashMap<String, INEDTypeInfo> map;
        			if (node instanceof ChannelElement)
        				map = channels;
        			else if (node instanceof ChannelInterfaceElement)
        				map = channelInterfaces;
        			else if (node instanceof SimpleModuleElement)
        				map = modules;
        			else if (node instanceof CompoundModuleElement)
        				map = modules;
        			else if (node instanceof ModuleInterfaceElement)
        				map = moduleInterfaces;
        			else 
        				throw new RuntimeException("internal error: unrecognized NED type " + node);

        			map.put(name, typeInfo);
        			components.put(name, typeInfo);
        		}

        		// add to the name list even if it was duplicate
        		reservedNames.add(name);
        	}
        }

        // now we should remove all types that were duplicates
        for (String dupName : duplicates) {
            channels.remove(dupName);
            channelInterfaces.remove(dupName);
            modules.remove(dupName);
            moduleInterfaces.remove(dupName);
            components.remove(dupName);
        }

        // validate the NED trees (check cross-references etc)
        for (IFile file : nedFiles.keySet()) {
            NEDFileValidator validator = new NEDFileValidator(this, new NEDMarkerErrorStore(file, markerSync, NEDMarkerErrorStore.NEDCONSISTENCYPROBLEM_MARKERID));
            NedFileElement tree = nedFiles.get(file);
            validator.validate(tree);
        }

        long dt = System.currentTimeMillis() - startMillis;
        System.out.println("rehashIfNeeded(): " + dt + "ms, " + markerSync.getNumberOfMarkers() + " markers on " + markerSync.getNumberOfFiles() + " files");

        // we need to do the synchronization in a background job, to avoid deadlocks 
        markerSync.runAsWorkspaceJob();

    }

    protected static Map<String,INedTypeElement> collectTypesFrom(INEDElement tree) {
    	Map<String,INedTypeElement> result = new LinkedHashMap<String,INedTypeElement>();
    	doCollectTypes("", tree, result);
    	return result;
    }

    protected static void doCollectTypes(String namePrefix, INEDElement parent, Map<String, INedTypeElement> result) {
        for (INEDElement node : parent) {
            if (node instanceof INedTypeElement) {
            	// collect this type
            	INedTypeElement typeNode = (INedTypeElement)node;
				result.put(namePrefix + typeNode.getName(), typeNode);
				
				// collect its inner types
				INEDElement typesSection = typeNode.getFirstChildWithTag(NEDElementTags.NED_TYPES); 
				if (typesSection != null)
					doCollectTypes(namePrefix + typeNode.getName() + ".", typesSection, result);
            }
        }
	}

	public synchronized void invalidate() {
		needsRehash = true;
    }

    /**
     * Reads all NED files in the project.
     */
    public synchronized void readAllNedFilesInWorkspace() {
        // do not allow access to the plugin until the whole parsing is finished
        try {
            // disable all ned model notifications until all files have been processed
            nedModelChangeNotificationDisabled = true;
            debugRehashCounter = 0;
            IResource wsroot = ResourcesPlugin.getWorkspace().getRoot();
            final ProblemMarkerSynchronizer sync = new ProblemMarkerSynchronizer();
            wsroot.accept(new IResourceVisitor() {
                public boolean visit(IResource resource) {
                    if (isNEDFile(resource))
                        readNEDFile((IFile) resource, sync);
                    return true;
                }
            });
            sync.runAsWorkspaceJob();
            rehashIfNeeded();
        } catch (CoreException e) {
            NEDResourcesPlugin.logError("Error during workspace refresh: ",e);
        } finally {
            nedModelChangeNotificationDisabled = false;
            Assert.isTrue(debugRehashCounter <= 1,"Too many rehash operation in readAllNedFilesInWorkspace()");
            nedModelChanged(new NEDModelEvent(null));
        }
    }

    // ******************* notification helpers ************************************

    public void addNEDComponentChangeListener(INEDChangeListener listener) {
        if (nedComponentChangeListenerList == null)
            nedComponentChangeListenerList = new NEDChangeListenerList();
        nedComponentChangeListenerList.add(listener);
    }

    public void removeNEDComponentChangeListener(INEDChangeListener listener) {
        if (nedComponentChangeListenerList != null)
        	nedComponentChangeListenerList.remove(listener);
    }

    public void addNEDModelChangeListener(INEDChangeListener listener) {
        if (nedModelChangeListenerList == null)
            nedModelChangeListenerList = new NEDChangeListenerList();
        nedModelChangeListenerList.add(listener);
    }

    public void removeNEDModelChangeListener(INEDChangeListener listener) {
        if (nedModelChangeListenerList != null)
            nedModelChangeListenerList.remove(listener);
    }

    /**
     * Respond to model changes
     */
    protected void nedModelChanged(NEDModelEvent event) {
        if (nedModelChangeNotificationDisabled)
            return;
        // System.out.println("NEDRESOURCES NOTIFY: "+event);
        // fire a component changed event if inheritance, naming or visual representation
        // has changed
        boolean inheritanceChanged = inheritanceMayHaveChanged(event);
        if (inheritanceChanged) {
            // System.out.println("Invalidating because of: " + event);
        	rehash();
        }
        // a top level component has changed (name, inheritance, display string)
        if (inheritanceChanged || displayStringMayHaveChanged(event)) {
            // notify component listeners (i.e. palette manager, where only the component name and
            // icon matters)
            if (nedComponentChangeListenerList != null)
                nedComponentChangeListenerList.fireModelChanged(event);
        }

        // notify generic listeners (like NedFileEditParts who refresh themselves
        // in response to this notification)
        // long startMillis = System.currentTimeMillis();

        if (nedModelChangeListenerList != null)
            nedModelChangeListenerList.fireModelChanged(event);

        // long dt = System.currentTimeMillis() - startMillis;
        // System.out.println("visual notification took " + dt + "ms");
    }

    /**
     * Returns whether the inheritance chain has changed somewhere
     * (name, extends, adding and removing top level nodes).
     */
    protected static boolean inheritanceMayHaveChanged(NEDModelEvent event) {
    	if (event.getSource() == null)
    		return true;
 
        // if we have changed a toplevel element's name we should rehash
        if (event.getSource() instanceof INedTypeElement && event instanceof NEDAttributeChangeEvent
                && SimpleModuleElement.ATT_NAME.equals(((NEDAttributeChangeEvent) event).getAttribute()))
            return true;

        // check for removal or insertion of top level nodes
        if (event.getSource() instanceof NedFileElementEx && event instanceof NEDStructuralChangeEvent)
            return true;

        // check for extends name change
        if (event.getSource() instanceof ExtendsElement && event instanceof NEDAttributeChangeEvent
                && ExtendsElement.ATT_NAME.equals(((NEDAttributeChangeEvent) event).getAttribute()))
            return true;
        // refresh if we have removed an extend node
        if (event instanceof NEDStructuralChangeEvent && ((NEDStructuralChangeEvent) event).getChild() instanceof ExtendsElement
                && ((NEDStructuralChangeEvent) event).getType() == NEDStructuralChangeEvent.Type.REMOVAL)
            return true;

        // TODO missing the handling of type, like attribute change, interface
        // node insert,remove, change

        // none of the above, so do not refresh
        return false;
    }

    /**
     * Returns whether the display string has changed in the event.
     */
    protected static boolean displayStringMayHaveChanged(NEDModelEvent event) {
    	return true; //TODO for now; then we can optimize later
//    	// overly cautious check: if anything within a "parameters" section 
//    	// was affected, or the removed subtree contains a "parameters" section,
//    	// we assume it was a display string change 
//		if (event.getSource().getParentWithTag(NEDElementTags.NED_PARAMETERS) != null)
//    		return true;
//		if (event instanceof NEDStructuralChangeEvent) {
//			NEDStructuralChangeEvent structuralChangeEvent = (NEDStructuralChangeEvent) event;
//			if (structuralChangeEvent.getChild().getParentWithTag(NEDElementTags.NED_PARAMETERS) != null)
//				return true;
//		}
//        return false;
    }

    /**
     * Synchronize the plugin with the resources in the workspace
     */
    public synchronized void resourceChanged(IResourceChangeEvent event) {
        try {
            if (event.getDelta() == null)
                return;
            // printResourceChangeEvent(event);

            final ProblemMarkerSynchronizer sync = new ProblemMarkerSynchronizer();
            event.getDelta().accept(new IResourceDeltaVisitor() {
                public boolean visit(IResourceDelta delta) throws CoreException {
                    IResource resource = delta.getResource();
                    // continue visiting the children if it is not a NED file
                    if (!isNEDFile(resource))
                        return true;

                    // printDelta(delta);
                    switch (delta.getKind()) {
                        case IResourceDelta.REMOVED:
                            // handle removed resource
                            forgetNEDFile((IFile) resource);
                            invalidate();
                            break;
                        case IResourceDelta.ADDED:
                            readNEDFile((IFile) resource, sync);
                            invalidate();
                            break;
                        case IResourceDelta.CHANGED:
                            // handle changed resource, but we are interested
                            // only in content change
                            // we don't care about marker and property changes
                            if ((delta.getFlags() & IResourceDelta.CONTENT) != 0) {
                                readNEDFile((IFile) resource, sync);
                                invalidate();
                            }
                            break;
                    }
                    // ned files do not have children
                    return false;
                }
            });
            sync.runAsWorkspaceJob();
        } catch (CoreException e) {
            NEDResourcesPlugin.logError("Error during workspace change notification: ", e);
        } finally {
            rehashIfNeeded();
        }

    }

    // Utility functions for debugging
    public static void printResourceChangeEvent(IResourceChangeEvent event) {
        System.out.println("event type: "+event.getType());
    }

    public static void printDelta(IResourceDelta delta) {
        System.out.println("  delta: "+delta.getResource().getProjectRelativePath()+" kind:"+delta.getKind()+
                " isContent:"+((delta.getFlags() & IResourceDelta.CONTENT) != 0)+
                " isMarkerChange:"+((delta.getFlags() & IResourceDelta.MARKERS) != 0));
    }

	public int getConnectCount(IFile file) {
		return connectCount.containsKey(file) ? connectCount.get(file) : 0;
	}
}
