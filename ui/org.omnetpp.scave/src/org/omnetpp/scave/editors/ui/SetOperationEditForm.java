package org.omnetpp.scave.editors.ui;

import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.omnetpp.scave.engine.ResultFileManager;
import org.omnetpp.scave.model.Dataset;
import org.omnetpp.scave.model.ScaveModelPackage;
import org.omnetpp.scave.model.SetOperation;
import org.omnetpp.scave.model2.Filter;
import org.omnetpp.scave.model2.FilterHints;
import org.omnetpp.scave.model2.ScaveModelUtil;

/**
 * Base class for edit forms of set operations.
 *
 * @author tomi
 */
public class SetOperationEditForm implements IScaveObjectEditForm {

	protected static final EStructuralFeature[] features = new EStructuralFeature[] {
		ScaveModelPackage.eINSTANCE.getSetOperation_FilterPattern(),
		ScaveModelPackage.eINSTANCE.getSetOperation_SourceDataset(),
	};
	
	/**
	 * The edited SetOperation.
	 */
	protected SetOperation setOperation;
	
	/**
	 * List of datasets that can be the source of this operation.
	 * First elements is <code>null</code> corresponding to "All".
	 */
	protected java.util.List<Dataset> sourceDatasets;

	protected FilterHints filterHints; 
	
	// controls
	private CCombo sourceDatasetCombo;
	private Text filterPatternText;
	//FIXME add also radiobutton or combo for ResultType
	
	public SetOperationEditForm(SetOperation setOperation, ResultFileManager manager) {
		this.setOperation = setOperation;
		
		sourceDatasets = new java.util.ArrayList<Dataset>();
		sourceDatasets.add(null);
		Dataset dataset = ScaveModelUtil.findEnclosingObject(setOperation, Dataset.class);
		java.util.List<Dataset> datasets = ScaveModelUtil.findDatasets(setOperation.eResource());
		for (Dataset ds : datasets)
			if (!ds.equals(dataset))
				sourceDatasets.add(ds);
		
		filterHints = new FilterHints(manager, this.setOperation.getType());
	}
	
	public String getTitle() {
		return String.format("%s operation", setOperation.eClass().getName());
	}

	public String getDescription() {
		return "Modify the properties of the operation.";
	}

	public int getFeatureCount() {
		return features.length;
	}

	public EStructuralFeature[] getFeatures() {
		return features;
	}

	public void populatePanel(Composite panel) {
		Label label;
		Group group;

		panel.setLayout(new GridLayout(2, false));
		
		// source dataset
		label = new Label(panel, SWT.NONE);
		label.setText("Source dataset:");
		label.setLayoutData(new GridData());
		sourceDatasetCombo = new CCombo(panel, SWT.BORDER | SWT.READ_ONLY);
		sourceDatasetCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		sourceDatasetCombo.setVisibleItemCount(15);
		String[] datasetNames = new String[sourceDatasets.size()];
		datasetNames[0] = "All";
		for (int i = 1; i < sourceDatasets.size(); ++i)
			datasetNames[i] = sourceDatasets.get(i).getName();
		sourceDatasetCombo.setItems(datasetNames);

		group = new Group(panel, SWT.NONE);
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		group.setLayout(new GridLayout(2, false));
		
		label = new Label(group, SWT.NONE);
		label.setText("Filter pattern:");
		label.setLayoutData(new GridData());
		filterPatternText = new Text(group, SWT.BORDER);
		filterPatternText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}
	
	public Object getValue(EStructuralFeature feature) {
		switch (feature.getFeatureID()) {
		case ScaveModelPackage.SET_OPERATION__FILTER_PATTERN:
			return filterPatternText.getText();
		case ScaveModelPackage.SET_OPERATION__SOURCE_DATASET:
			int index = sourceDatasetCombo.getSelectionIndex();
			return index >= 0 ? sourceDatasets.get(index) : null;
		default:
			throw new IllegalArgumentException("Unexpected feature: " + feature.getName());
		}
		
	}

	public void setValue(EStructuralFeature feature, Object value) {
		switch (feature.getFeatureID()) {
		case ScaveModelPackage.SET_OPERATION__FILTER_PATTERN:
			if (value != null) filterPatternText.setText((String)value);
			break;
		case ScaveModelPackage.SET_OPERATION__SOURCE_DATASET:
			sourceDatasetCombo.setText(value != null ? ((Dataset)value).getName() : "All");
			break;
		default:
			throw new IllegalArgumentException("Unexpected feature: " + feature.getName());
		}
	}
}

