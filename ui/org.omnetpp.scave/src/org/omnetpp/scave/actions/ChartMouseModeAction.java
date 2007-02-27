package org.omnetpp.scave.actions;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.omnetpp.common.canvas.ZoomableCanvasMouseSupport;
import org.omnetpp.common.image.ImageFactory;
import org.omnetpp.scave.charting.ChartCanvas;
import org.omnetpp.scave.editors.ScaveEditor;
import org.omnetpp.scave.editors.ui.ChartPage;
import org.omnetpp.scave.editors.ui.ScaveEditorPage;

/**
 * Switches mouse mode between zoom/pan for chart on the active chart page in the active Scave editor.
 *
 * @author andras
 */
public class ChartMouseModeAction extends AbstractScaveAction {
	private int destMode;

	public ChartMouseModeAction(int destMode) {
		super(IAction.AS_RADIO_BUTTON);
		this.destMode = destMode;
		if (destMode==ZoomableCanvasMouseSupport.PAN_MODE) {
			setText("Hand tool");
			setDescription("Lets you move the chart using the mouse; hold down Ctrl for zooming.");
			setImageDescriptor(ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_HAND));
		}
		else if (destMode==ZoomableCanvasMouseSupport.ZOOM_MODE) {
			setText("Zoom tool");
			setDescription("Lets you zoom the chart using the mouse; use Shift for zooming out, or hold down Ctrl for moving the chart.");
			setImageDescriptor(ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_ZOOM));
		}
		else {
			Assert.isTrue(false);
		}
	}

	@Override
	protected void doRun(ScaveEditor scaveEditor, IStructuredSelection selection) {
		ScaveEditorPage page = scaveEditor.getActiveEditorPage();
		if (page != null && page instanceof ChartPage) {
			ChartCanvas canvas = ((ChartPage)page).getChartView();
			canvas.setMouseMode(destMode);
			this.setChecked(true); 
			scaveEditor.fakeSelectionChange(); // fire an update so that the other tool gets unchecked
		}
	}

	@Override
	protected boolean isApplicable(ScaveEditor editor, IStructuredSelection selection) {
		ChartCanvas canvas = getChart(editor, selection);
		if (canvas!=null)
			setChecked(canvas.getMouseMode()==destMode); //FIXME we probably get invoked all too rarely for this to be useful...
		return canvas!=null;
	}
	
	protected ChartCanvas getChart(ScaveEditor editor, IStructuredSelection selection) {
		ScaveEditorPage page = editor.getActiveEditorPage();
		if (page == null || !(page instanceof ChartPage)) 
			return null;
		ChartCanvas canvas = ((ChartPage)page).getChartView();
		return canvas;
	}
}
