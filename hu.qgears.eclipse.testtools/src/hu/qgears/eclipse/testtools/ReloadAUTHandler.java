package hu.qgears.eclipse.testtools;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.rcptt.ui.launching.aut.AUTManagerView;
import org.eclipse.rcptt.ui.launching.aut.AutElement;
import org.eclipse.ui.PlatformUI;

public class ReloadAUTHandler extends AbstractHandler {

	private static final String RCPTT_APPLICATIONS_VIEW_ID = "org.eclipse.rcptt.ui.aut.manager";

	public void reload() throws Exception{
		AutElement el = findAutElement() ;
		el.getAut().launch(new NullProgressMonitor());
	}

	private AutElement findAutElement() throws Exception {
		AUTManagerView autView = (AUTManagerView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(RCPTT_APPLICATIONS_VIEW_ID);
		if (autView == null){
			throw new Exception("Cannot activate view : "+ RCPTT_APPLICATIONS_VIEW_ID);
		} else {
			ISelection sel = autView.getSite().getSelectionProvider().getSelection();
			if (!sel.isEmpty() && sel instanceof StructuredSelection){
				return (AutElement) ((StructuredSelection)sel).getFirstElement();
			}
			throw new Exception ("Cannot find active AUT");
		}
	}


	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			reload();
		} catch (Exception e) {

				throw new ExecutionException("Reloading aut failed",e);
		}
		return null;
	}
	
}
