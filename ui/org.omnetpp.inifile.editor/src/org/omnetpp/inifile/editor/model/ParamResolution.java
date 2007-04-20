package org.omnetpp.inifile.editor.model;

import org.omnetpp.ned.model.pojo.ParamNode;
import org.omnetpp.ned.model.pojo.SubmoduleNode;

/**
 * Value object, stores the result of a parameter resolution.
 */
public class ParamResolution {
	
	public enum ParamResolutionType {
		UNASSIGNED, // unassigned parameter
		NED, // parameter assigned in NED
		NED_DEFAULT, // parameter set to the default value (**.apply-default=true)
		INI, // parameter assigned in inifile, with no default in NED file
		INI_OVERRIDE, // inifile setting overrides NED default
		INI_NEDDEFAULT, // inifile sets param to its NED default value
	}
	
	// moduleFullPath and param name (from paramDeclNode or paramValueNode) identify the NED parameter. 
	// For vector submodules, moduleFullPath contains "[*]".
	// pathModules[] relates moduleFullPath to NEDElements. The network is pathModules[1]'s
	// parent CompoundModuleNode, or paramDeclNode's parent Compound/SimpleModuleNode if 
	// pathModules[] is empty. After that, the type of pathModules[i] is the parent 
	// Compound/SimpleModuleNode of the next pathModule (or finally, the paramDeclNode).
	// pathModules[0] may be null.
	public String moduleFullPath;
	public SubmoduleNode[] pathModules;
	public ParamNode paramDeclNode;  // node where param was declared; not null
	public ParamNode paramValueNode;  // node where param gets assigned (may be a module or submodule param, or may be null)

	// how the parameter value gets resolved: from NED, from inifile, unassigned, etc
	public ParamResolutionType type;

	// during analysis of which section
	public String activeSection; 

	// section+key identify the value assignment in the inifile; 
	// they are null if parameter is assigned from NED
	//XXX add IFile ?   
	public String section;
	public String key;
	
	// for convenience
	public ParamResolution(String moduleFullPath, SubmoduleNode[] pathModules, 
			               ParamNode paramValueNode, ParamNode paramDeclNode, ParamResolutionType type, 
			               String activeSection, String section, String key) {
		this.moduleFullPath = moduleFullPath;
		this.pathModules = pathModules;
		this.paramValueNode = paramValueNode;
		this.paramDeclNode = paramDeclNode;
		this.type = type;
		this.activeSection = activeSection;
		this.section = section;
		this.key = key;
	}

	/* (non-Javadoc)
	 * generated by Eclipse; needed for GenericTreeUtil.treeEquals()
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ParamResolution other = (ParamResolution) obj;
		if (activeSection == null) {
			if (other.activeSection != null)
				return false;
		} else if (!activeSection.equals(other.activeSection))
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (moduleFullPath == null) {
			if (other.moduleFullPath != null)
				return false;
		} else if (!moduleFullPath.equals(other.moduleFullPath))
			return false;
		if (paramDeclNode == null) {
			if (other.paramDeclNode != null)
				return false;
		} else if (!paramDeclNode.equals(other.paramDeclNode))
			return false;
		if (paramValueNode == null) {
			if (other.paramValueNode != null)
				return false;
		} else if (!paramValueNode.equals(other.paramValueNode))
			return false;
		if (section == null) {
			if (other.section != null)
				return false;
		} else if (!section.equals(other.section))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}
}

