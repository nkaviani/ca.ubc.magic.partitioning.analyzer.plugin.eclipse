package snapshots.views;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.ui.*;
import org.eclipse.swt.SWT;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import partitioner.views.ModelCreationEditor;
import plugin.Activator;
import plugin.Constants;

import snapshots.action.FinishAction;
import snapshots.action.JIPViewerAction;
import snapshots.com.mentorgen.tools.util.profile.Start;
import snapshots.controller.ControllerDelegate;
import snapshots.controller.IController;
import snapshots.events.logging.ErrorDisplayAction;
import snapshots.events.logging.EventLogActionHandler;
import snapshots.events.logging.EventLogEvent;
import snapshots.events.logging.EventLogger;
import snapshots.events.logging.LogAction;
import snapshots.model.ActiveSnapshotModel;
import snapshots.model.ISnapshotInfoModel;
import snapshots.model.Snapshot;

public class 
SnapshotView 
extends ViewPart 
implements IView
{
	public static final String ID 
		= "snapshots.views.SampleView";

	Text 							path_text;
	Text							name_text;
	Text							port_text;
	Text 							host_text;
	
	private IController 			controller 
		= new ControllerDelegate();
	EventLogTable					log_console_table;
	private	TreeViewer				snapshot_tree_viewer;
	private FileTreeContentProvider file_tree_content_provider;
	
	private ActiveSnapshotModel 	active_snapshot_model;

	public 
	SnapshotView() 
	{
		this.active_snapshot_model
			= new ActiveSnapshotModel();
	}

	@Override
	public void 
	dispose()
	{
		// temporary solution: implement true persistence later
		Activator.getDefault().persistTreeContentProvider(
			this.file_tree_content_provider
		);
		super.dispose();
	}
	
	public void 
	createPartControl
	( Composite parent ) 
	{
		this.file_tree_content_provider
			= (FileTreeContentProvider)
				Activator.getDefault().getTreeContentProvider();
		
		GridLayout parent_layout
			= new GridLayout(1, true);
		parent.setLayout(parent_layout);
		
		Group configuration_group
			= new Group(parent, SWT.SHADOW_ETCHED_IN | SWT.FILL);
		initializeGridLayout(configuration_group);
		configuration_group.setText("New Snapshot Properties");
		initializeInputRows(configuration_group);
		
		GridData grid_data 
			= new GridData(GridData.FILL_HORIZONTAL);
		configuration_group.setLayoutData(grid_data);
		
		Group tree_group
			= new Group(parent, SWT.SHADOW_ETCHED_IN);
		tree_group.setLayout(new GridLayout(1, false));
		grid_data
			= new GridData(GridData.FILL_BOTH);
		tree_group.setLayoutData(grid_data);
		tree_group.setText("Snapshots");
		
		this.snapshot_tree_viewer
			= new TreeViewer(tree_group, SWT.FILL);
		grid_data 
			= new GridData(GridData.FILL_BOTH);
		
		if( this.file_tree_content_provider == null ){
			this.file_tree_content_provider
				= new FileTreeContentProvider(this.getPreviousPath(), this.snapshot_tree_viewer); 
		}
		snapshot_tree_viewer.setContentProvider( 
			this.file_tree_content_provider
		);
		
		snapshot_tree_viewer.setLabelProvider( new FileTreeLabelProvider() );
		snapshot_tree_viewer.getTree().setLayoutData(grid_data);
		snapshot_tree_viewer.getTree().pack();
		snapshot_tree_viewer.setInput("hello");
		
		// from example code found in the eclipse plugins book
		// pp. 202
		this.snapshot_tree_viewer.addDoubleClickListener(
			new IDoubleClickListener() 
			{
				@Override
				public void 
				doubleClick
				( DoubleClickEvent event ) 
				{
					IStructuredSelection selection 
						= (IStructuredSelection) event.getSelection();
					Object[] objects 
						= selection.toArray();
					if(objects.length > 0){
						if( objects[0] instanceof VirtualModelFileInput){
							System.out.println("Opening model");
									
							IWorkbenchWindow window 
								= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
							IWorkbenchPage page = window.getActivePage();
							try {
								page.openEditor(
									(VirtualModelFileInput) objects[0],
									"plugin.views.model_creation_editor"
								);
							} catch (PartInitException e1) {
								e1.printStackTrace();
							}
						}
						else if( objects[0] instanceof File ){
							File potential_directory
								= (File) objects[0];
							if(potential_directory.isDirectory()){
								SnapshotView.this.setSnapshotPath(potential_directory);
							}
							else if(!potential_directory.isDirectory()){
								
								// files not in the workspace are not IFiles
								// the following example code is from 
								// http://www.eclipsezone.com/eclipse/forums/t102821.html
								File file = new File(potential_directory.getAbsolutePath());
								if( file.exists() && file.isFile() ){
									VirtualModelFileInput input 
										= SnapshotView.this.file_tree_content_provider
											.addVirtualModelInput( file.getAbsolutePath() );
									IWorkbenchWindow window 
										= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
									IWorkbenchPage page = window.getActivePage();
									try {
										page.openEditor(
											input,
											"plugin.views.model_creation_editor"
										);
									} catch (PartInitException e1) {
										e1.printStackTrace();
									}
									
									SnapshotView.this.refresh();
								}
							}
						}
					}
				}
			}
		);
		
		IActionBars actionBars 	
			= super.getViewSite().getActionBars();
		this.initializeToolbar(
			actionBars.getToolBarManager()
		);
			
		this.initializeDropDownMenu(actionBars.getMenuManager());
		
		this.controller.addView(this);
		this.controller.addModel( this.active_snapshot_model );
		
		this.log_console_table
			= new EventLogTable(parent, "Event Log", null);
		this.log_console_table.setContents(
			Activator.getDefault().getEventLogListModel().getEventLogList()
		);
		controller.addView(this);
		
		this.initializeEventLogActionHandler();
		this.initializeContextMenu();
		
		// the following code is a view-communication solution
		// found in:
		// http://tomsondev.bestsolution.at/2011/01/03/enhanced-rcp-how-views-can-communicate/
		BundleContext context 
			= FrameworkUtil.getBundle(ModelCreationEditor.class).getBundleContext();
		EventHandler handler 
			= new EventHandler() {
				public void handleEvent
				( final Event event )
				{
					// acceptable alternative, given that we run only
					// one display
					Display display 
						= Display.getDefault();
					assert( display != null) : "Display is null";
					if( display.getThread() == Thread.currentThread() ){
						Boolean refresh 
							= (Boolean) event.getProperty("REFRESH");
						if(refresh != null && refresh == true){
							SnapshotView.this.refresh();
						}
					}
					else {
						display.syncExec( 
							new Runnable() {
								public void 
								run()
								{
									Boolean refresh 
										= (Boolean) event.getProperty("REFRESH");
									if(refresh != null && refresh == true){
										SnapshotView.this.refresh();
									}
								}
							}
						);
					}
				}
			};
			
		Dictionary<String,String> properties 
			= new Hashtable<String, String>();
		properties.put(EventConstants.EVENT_TOPIC, "viewcommunication/*");
		context.registerService(EventHandler.class, handler, properties);
	}
	
	@Override
	public void 
	setFocus() 
	{
	}
	
	private void 
	setSnapshotPath
	( File potential_directory ) 
	// currently private
	// may become public if we have a handler call this
	// or not: I wonder if I can make a handler a private class...
	{
		SnapshotView.this.controller.setModelProperty(
			Constants.PATH_PROPERTY, 
			potential_directory.getAbsolutePath()
		);
	}

	public void
	initializeContextMenu()
	{
		// First we create a menu Manager
		MenuManager menuManager 
			= new MenuManager();
		Menu menu 
			= menuManager.createContextMenu(
				this.snapshot_tree_viewer.getTree()
			);
		// Set the MenuManager
		this.snapshot_tree_viewer.getTree().setMenu(menu);
		getSite().registerContextMenu(
			menuManager, 
			this.snapshot_tree_viewer
		);
		
		// Make the selection available
		getSite().setSelectionProvider(
			this.snapshot_tree_viewer
		);
	}
	
	private void 
	initializeToolbar
	(IToolBarManager toolBar) 
	{		
		this.controller.addView(this);	
		this.controller.addModel(Activator.getDefault().getEventLogListModel());
		
		IAction finish	
			= new FinishAction(
				this.controller
			);
		IAction start	
			= new StartAction(
				this.controller
			);
				
		toolBar.add(start);
		toolBar.add(finish);
	}
	
	private void 
	initializeInputRows
	( Composite parent ) 
	{
		Label path_label 
			= createLabel( parent, "Path: " );
		path_label.pack();
		
		this.path_text 
			= createPath( parent,1 );
		this.path_text.setLocation(80, 20);
		this.path_text.pack();
		this.path_text.setEditable(true);
		
		Button browse_button 
			= new Button( parent, SWT.NONE );
		browse_button.setText( "Browse" );
		browse_button.addSelectionListener( new SelectionAdapter(){
			public void widgetSelected( SelectionEvent event ){
				DirectoryDialog file_dialog 
					= new DirectoryDialog( 
						PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getShell(), 
						SWT.OPEN
					);
				file_dialog.setText("Select Directory");
				file_dialog.setFilterPath( path_text.getText() );
				String selected = file_dialog.open();
				if(selected != null){
					path_text.setText(selected);
				}
				
				// add the new directory to the list of directories in the
				// tree viewer
				SnapshotView.this.addFolder( selected );
			}
		});
		
		createLabel( parent, "Name: " );
		this.name_text 
			= createPath( parent, 2 );
		
		createLabel(parent, "Port: " );
		this.port_text 
			= createPath( parent, 2 );
		
		createLabel( parent, "Host: " );
		this.host_text 
			= createPath( parent, 2 );
		
		setWidgetText();
	}

	private void 
	initializeGridLayout
	(Composite parent) 
	{
		GridLayout grid_layout 
			= new GridLayout(3, false);
		parent.setLayout(grid_layout);
	}
	
	private Label 
	createLabel
	(Composite container, String label_text) 
	{
		Label local_label
			= new Label(container, SWT.LEFT);
		GridData grid_data 
			= new GridData(SWT.BEGINNING, SWT.FILL, false, false);
		local_label.setLayoutData(grid_data);
		local_label.setText(label_text);
		
		return local_label;
	}
	
	private Text 
	createPath
	(Composite container, int num_columns) 
	{
		Text local_text
			= new Text(container, SWT.SINGLE | SWT.BORDER);
		GridData grid_data 
			= new GridData( SWT.FILL, SWT.FILL, false, false, num_columns, 1);
		
		grid_data.grabExcessHorizontalSpace = true;
		grid_data.widthHint = 200;
		local_text.setLayoutData(grid_data);
		
		return local_text;
	}
	
	private void 
	initializeDropDownMenu
	(IMenuManager dropDownMenu) 
	{
		IAction jip_viewer
			= new JIPViewerAction();
		
		dropDownMenu.add(jip_viewer);
	}
	
	private void 
	setWidgetText()
	{
		this.path_text.setText(this.getPreviousPath());
		if(this.getPreviousName() == null){
			System.out.println("Null name odd");
		}
		this.name_text.setText(this.getPreviousName());
		this.port_text.setText(this.getPreviousPort());
		this.host_text.setText(this.getPreviousHost());
	}
	
	public String 
	getPreviousPath() 
	{
		String prevPath = null;
		
		prevPath 
			= this.active_snapshot_model
				.getSnapshotPath();
		if(prevPath == null){
			prevPath = ""; 
		}
		
		System.out.println("Previous Path " + prevPath);
		return prevPath;		
	}
	
	public String
	getPreviousName()
	{
		String prevName = null;
		prevName = this.active_snapshot_model.getSnapshotName();
		
		if(prevName == null){
			prevName = "";
		}
		
		return prevName;
	}
	
	public String
	getPreviousPort()
	{
		String prevPort = null;
		
		prevPort = this.active_snapshot_model.getSnapshotPort();
		if(prevPort == null || prevPort.equals("") ){
			prevPort = "15599";
		}
		
		return prevPort;
	}
	
	public String
	getPreviousHost()
	{
		String prevHost = null;
		
		prevHost = this.active_snapshot_model.getSnapshotHost();
		
		if(prevHost == null || prevHost.equals("") ){
			prevHost = "localhost";
		}
		
		return prevHost;
	}
	
	@Override
	public void 
	modelPropertyChange
	( PropertyChangeEvent evt ) 
	{
		System.out.println("modelPropertyChange(): Property changed");
		switch(evt.getPropertyName()){
		case Constants.PATH_PROPERTY:
			this.path_text.setText((String) evt.getNewValue());
			break;
		case Constants.HOST_PROPERTY:
			this.host_text.setText((String) evt.getNewValue());
			break;
		case Constants.NAME_PROPERTY:
			this.name_text.setText( (String) evt.getNewValue());
			break;
		case Constants.SNAPSHOT_CAPTURED_PROPERTY:
			this.snapshot_tree_viewer.setInput("hello");
			this.snapshot_tree_viewer.refresh();
			break;
		case Constants.PORT_PROPERTY:
			this.port_text.setText( (String) evt.getNewValue() );
			System.out.println(this.port_text.getText());
			System.out.println(this.host_text.getText());
			break;
		case Constants.EVENT_LIST_PROPERTY:
			this.log_console_table.refresh();
			break;
		}
		this.refresh();
	}
	
	public void
	refresh()
	// the following code is from:
	// http://cvalcarcel.wordpress.com/tag/setexpandedelements/
	{
		System.out.println("Refreshing the snapshot view");
		Object[] treePaths 
			= this.snapshot_tree_viewer.getExpandedElements();
		this.snapshot_tree_viewer.refresh();
		this.snapshot_tree_viewer.setExpandedElements(treePaths);
	}
	
	private void 
	initializeEventLogActionHandler() 
	{
		EventLogActionHandler action_handler
			= Activator.getDefault().getActionHandler();
		
		action_handler.registerAction(
			Constants.ACTKEY_ERROR_DISPLAY,
			new ErrorDisplayAction()
		);
		action_handler.registerAction(
			Constants.ACTKEY_LOG_DISPLAY, 
			new LogAction()
		);
	}
	
	public void 
	displayErrorDialog
	(Shell shell, String string) 
	{
		ErrorDialog.openError(
				shell, 
				null, 
				null,
				new Status(
					IStatus.ERROR, 
					Activator.PLUGIN_ID, 
					IStatus.OK, 
					string,
					null
				)
			);
	}
	
	public void
	clearSnapshots()
	{
		FileTreeContentProvider file_tree_content_provider 
			= this.getSnapshotTreeContentProvider();
		file_tree_content_provider.clear();
		this.refresh();
	}

	public void 
	removeFolder
	(File folder)
	{
		FileTreeContentProvider file_tree_content_provider
			= this.getSnapshotTreeContentProvider();
		file_tree_content_provider.remove(folder);
		this.refresh();
	}
	
	private FileTreeContentProvider
	getSnapshotTreeContentProvider()
	{
		IContentProvider cp 
			= this.snapshot_tree_viewer.getContentProvider();
		FileTreeContentProvider fcp
			= (FileTreeContentProvider) cp;
		
		return fcp;
	}

	public void
	addFolder
	( String path )
	{
		FileTreeContentProvider file_tree_content_provider
			= this.getSnapshotTreeContentProvider();
		file_tree_content_provider.add(path);
		this.refresh();
	}
	
	class 
	StartAction 
	extends Action 
	implements IView
	{
		private IController 				controller;
		private EventLogger 				event_logger;
		
		public 
		StartAction
		(	IController controller )
		{
			this.controller
				= controller;
			this.event_logger
				= new EventLogger();
			
			this.setToolTipText
			("Record a profile snapshot.");
			this.setEnabled(true);
			this.controller.addView(this);
		}
		
		@Override
		public void
		setEnabled
		(boolean enabled)
		{
			super.setEnabled(enabled);
			
			String image_path
				= enabled 
				? "icons/record.png"
				: "icons/record_inactive.png";
		
			this.setImageDescriptor
			(Activator.getImageDescriptor(image_path));
		}
		
		@Override
		public void 
		run()
		{
			// the start action is now also responsible for
			// implementing the data check functionality
			if( !this.values_present() )
				return;

			// returned array should be ordered as in gui layout
			this.controller.setModelProperty(
				Constants.PATH_PROPERTY, SnapshotView.this.path_text.getText()
			);
			this.controller.setModelProperty(
				Constants.NAME_PROPERTY, SnapshotView.this.name_text.getText()
			);
			this.controller.setModelProperty(
				Constants.HOST_PROPERTY, SnapshotView.this.host_text.getText()
			);
			this.controller.setModelProperty(
				Constants.PORT_PROPERTY, SnapshotView.this.port_text.getText()
			);
			
		    Snapshot snapshot 
		    	= this.getSnapshot();
		    
		    try{
		    	this.inner_run(snapshot);
		    }
		    catch(IOException ex){
	        	ex.printStackTrace();
	        }
		}
		
		public boolean 
		values_present() 
		// the system will also validate the inputed data when it
		// attempts to create the snapshot
		{
			Shell shell
				= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
			
			StringBuilder error_message 
				= new StringBuilder();
			
			if( SnapshotView.this.path_text.getText().equals("") ){
				error_message.append("The directory path cannot be empty\n");
			}
			if( !this.validPortEntry( SnapshotView.this.port_text.getText()) ){
				error_message.append(
					"The port entry cannot be empty and must contain only numbers\n"
				);
			}
			if( SnapshotView.this.host_text.getText().equals("") ){
				error_message.append("The host name cannot be empty\n");
			}
			
			if(error_message.length() != 0 ){
				SnapshotView.this
					.displayErrorDialog(shell, error_message.toString());
			}
			return error_message.length() == 0;
		}
		
		private boolean 
		validPortEntry
		(String str) 
		{
			 if(str != null && str.length() > 0) {
	            int length = str.length();
	            boolean accept = true;
	            for(int idx = 0; idx < length && accept; idx++) {
	                accept = Character.isDigit(str.charAt(idx));
	            }
	            if(accept) {
	                return true;
	            }
	        }
			 
			return false;
		}

		private void 
		inner_run
		(Snapshot snapshot) 
		throws IOException 
		{
		    if (snapshot != null) {
		    	boolean gotException = false;
		    	try {
		    		snapshots.com.mentorgen.tools.util.profile.File.doFile(
		    			snapshot.getHost(),
		    			snapshot.getPort(),
		    			snapshot.getPathAndName());
		    	}
		    	catch (IOException ioex) {
		    		gotException = true;
		        	this.event_logger.updateConsoleLog(ioex);
		    	}
				      
		    	// if no exception, ie the above succeeded, then go to start
		    	// command and report file success
		    	if (!gotException) {
		    		this.event_logger.updateForSuccessfulCall(
		    			"file"
		    		);
		    		try {
		    			Start.doStart(snapshot.getHost(),
		    					snapshot.getPort());
		    		}
		    		catch (IOException ioex) {
		    			gotException = true;
		    			this.event_logger.updateConsoleLog(ioex);
		    		}
		    	}
		    	// if no exception, ie the above succeeded, then
		    	//report start success and send event
		    	if (!gotException) {
		    	  	this.event_logger.updateForSuccessfulCall(
		    	  		"start"
		    	  	);
					this.controller.alertPeers(
						Constants.SNAPSHOT_STARTED,
						this,
					    snapshot
					);
					this.controller.setModelProperty(
						Constants.NAME_PROPERTY, 
						snapshot.getName()
					);
					this.setEnabled(false);
		    	}
		    } 
		}

		private Snapshot 
		getSnapshot() 
		{
			ISnapshotInfoModel info_model
				= SnapshotView.this.active_snapshot_model;
			
		    // check path specified
		    String path = info_model.getSnapshotPath();	    
		    if (path == null || path.trim().length() == 0) {
		    	EventLogEvent event
		    		= this.event_logger.getErrorEvent();
		    	event.addProperty(
		    		Constants.KEY_ERR_MSSG, 
		    		"A folder in which to store the snapshot " 
		    		+ "must be specified."
		    	);
		    	EventLogActionHandler action_handler
		    		= Activator.getDefault().getActionHandler();
		    	action_handler.performActionByKey(
		    		Constants.ACTKEY_ERROR_DISPLAY, 
		    		event
		    	);
		    	return null; 
		    }
		    
		    File pathFile = new File(path);
		    if (!pathFile.exists() || !pathFile.isDirectory()) {
		    	EventLogEvent event
		    		= this.event_logger.getErrorEvent();
		    	event.addProperty(
		    		Constants.KEY_ERR_MSSG,
		    		"The folder ({0}) does not exist."
		    	);
		    	event.addProperty(
		    		Constants.KEY_ERR_VALUES,
		    		new Object[]{ pathFile.getPath() }
		    	);
		    	EventLogActionHandler action_handler
		    		= Activator.getDefault().getActionHandler();
		    	action_handler.performActionByKey(
		    		Constants.ACTKEY_ERROR_DISPLAY,
		    		event
		    	);
		    	
		    	return null; 
		    }
		    
		    if (!pathFile.canWrite() || !pathFile.canRead()) {
		    	EventLogEvent event
		    		= this.event_logger.getErrorEvent();
		    	event.addProperty(
		    		Constants.KEY_ERR_MSSG, 
		    		"The folder ({0}) must have both read and "
		    		+ "write access."
		    	);
		    	event.addProperty(
		    		Constants.KEY_ERR_VALUES, 
		    		new Object[]{pathFile.getPath()}
		    	);
		    	EventLogActionHandler event_handler
		    		= Activator.getDefault().getActionHandler();
		    	event_handler.performActionByKey(
		    		Constants.ACTKEY_ERROR_DISPLAY,
		    		event
		    	);
		    	
		    	return null;
		    }
		    
		    String port 		
		    	= info_model.getSnapshotPort();
		    String host 		
		    	= info_model.getSnapshotHost();
		    final String origName 	
		    	= info_model.getSnapshotName();
		    
		    String newName 				
		    	=  this.setNewName(origName, pathFile);
		   
		    return new Snapshot(
		        pathFile.getPath(),
		        newName,
		        origName,
		        port,
		        host
		    );
		  }
		
		private String 
		setNewName
		(String origName, File pathFile) 
		{
			String newName;
			
			// modify name if it already exists and is not empty
		    if (origName.length() > 0) {
		      boolean nameExists = true;
		      String tempName = origName;
		      String nameSuffix = ".txt";
		      if (	origName.endsWith(".txt") 
		    		|| origName.endsWith(".xml")) 
		      {
		        tempName 
		        	= origName.substring(0, origName.length() - 4);
		        nameSuffix 
		        	= origName.substring(origName.length() - 4);
		      }
		      StringBuilder buf = new StringBuilder(tempName);
		      int baseLength = buf.length();
		      for (int cnt = 0; nameExists; cnt++) {
		        buf.setLength(baseLength);
		        if (cnt > 0) {
		          buf.append(cnt);
		        }
		        buf.append(nameSuffix);
		        File txtPath = new File(pathFile, buf.toString());
		        nameExists = txtPath.exists();
		      }
		      newName = buf.toString();
		    }
		    // name is empty, create name based on current time
		    // but leave origName empty
		    else {
		      StringBuilder buf = new StringBuilder();
		      buf.append(
		    	new SimpleDateFormat("yyyyMMdd-HHmmss").format(
		    		new Date()
		    	)
		    	);
		      buf.append(".txt");
		      newName = buf.toString();
		    }
		    return newName;
		}

		@Override
		public void 
		modelPropertyChange
		( PropertyChangeEvent evt ) 
		{
			switch (evt.getPropertyName()) {
			    case Constants.SNAPSHOT_CAPTURED_PROPERTY:
			    case Constants.SNAPSHOT_CAPTURE_FAILED:
				    this.setEnabled(true);
				    break;
			    default:
			    	System.out.println("StartAction swallowed message.");
			    	break;
			}
		}
	}
}

// note : the following class and the next modeled themselves
// after the examples provided in 
// http://www.java2s.com/Code/Java/SWT-JFace-Eclipse/DemonstratesTreeViewer.htm
class 
FileTreeContentProvider 
implements ITreeContentProvider
{
	Set<File> snapshot_folders
		= new CopyOnWriteArraySet<File>();
	Map<String, Integer> snapshots_map
		= new HashMap<String, Integer>();
	Map<String, Set<File>> snapshot_models;
	
	public
	FileTreeContentProvider
	( String path, TreeViewer tree_viewer )
	{
		File default_directory
			= new File(path);
		if(default_directory.exists()){
			this.snapshot_folders.add( default_directory );
		}
		this.snapshot_models
			= new HashMap<String, Set<File>>();
	}
	
	public VirtualModelFileInput 
	addVirtualModelInput
	( String absolute_path ) 
	{
		int count = 1;
		Set<File> models = null;
		
		// if contained, 
		if( this.snapshot_models.containsKey(absolute_path) ){
			count = this.snapshots_map.get(absolute_path) + 1;
			models = this.snapshot_models.get(absolute_path);
		}
		else {
			models = new TreeSet<File>( 
				new Comparator<File>(){
					@Override
					public int compare(File arg0, File arg1) {
						return arg0.getName().compareTo(arg1.getName());
					}
				});
			this.snapshot_models.put(absolute_path, models);
		}
		
		String virtual_file_name
			= "Model " + count;
		VirtualModelFile virtual_file 
			= new VirtualModelFile(absolute_path, virtual_file_name );
		VirtualModelFileInput return_value
			= new VirtualModelFileInput(absolute_path, virtual_file);
	
		models.add(return_value);
		this.snapshots_map.put(absolute_path, count);
		
		return return_value;
	}

	public void 
	add
	( String path ) 
	{
		File directory
			= new File( path );
		snapshot_folders.add(directory);
	}

	public void 
	remove
	( File folder ) 
	{
		this.snapshot_folders.remove(folder);
	}

	public void 
	clear()
	{
		this.snapshot_folders.clear();
	}

	public Object[] 
	getChildren
	( Object arg0 ) 
	{
		File parent
			= (File) arg0;
		List<File> xml_files
			= new ArrayList<File>();
		File[] children = parent.listFiles();
		if(children != null){
			for(File f : children){
				if(f.getName().endsWith(".xml") || f.getName().endsWith(".XML") || f.isDirectory()){
					xml_files.add(f);
				}
			}
			return xml_files.toArray();
		}
		else if(parent.isFile()){
			Set<File> child_list 
				= this.snapshot_models.get(parent.getAbsolutePath());
			if(child_list != null){
				children = child_list.toArray(new File[0]);
			}
		}
		return children;
	}
	
	public Object 
	getParent
	( Object arg0 ) 
	{
		return ((File) arg0).getParentFile();
	}
	
	public boolean 
	hasChildren
	( Object arg0 ) 
	{
		Object[] obj = getChildren( arg0 );
	
		return obj == null ? false : obj.length > 0;
	}

	public Object[] 
	getElements
	( Object arg0 ) 
	{
		return this.snapshot_folders.toArray();
	}

	public void 
	dispose() 
	{
		// Nothing to dispose
	}

	@Override
	public void 
	inputChanged
	( Viewer arg0, Object arg1, Object arg2 ) 
	{
	}
}

class 
FileTreeLabelProvider 
implements ILabelProvider 
{
  private List<ILabelProviderListener> listeners;

  private Image file;
  private Image dir;

  public 
  FileTreeLabelProvider() 
  {
	this.listeners 
		= new ArrayList<ILabelProviderListener>();
  }

  public Image 
  getImage
  ( Object arg0 ) 
  {
	  if( this.file == null){
		  this.file 
	    	= PlatformUI.getWorkbench()
	    	.getSharedImages()
	    	.getImage(ISharedImages.IMG_OBJ_FILE); 
	  }
	  if( this.dir == null ){
		  this.dir 
	    	= PlatformUI.getWorkbench()
	    	.getSharedImages()
	    	.getImage(ISharedImages.IMG_OBJ_FOLDER); 
	  }
	  
    return ((File) arg0).isDirectory() ? dir : file;
  }

  public String 
  getText
  ( Object arg0 ) 
  {
    String text 
    	= ((File) arg0).getName();

    if (text.length() == 0) {
      text = ((File) arg0).getPath();
    }

    return text;
  }
 
  public void 
  dispose() 
  {
  }

  public boolean 
  isLabelProperty
  ( Object arg0, String arg1 ) 
  {
    return false;
  }

  @Override
  public void 
  addListener
  ( ILabelProviderListener arg0 ) 
  {
    listeners.add(arg0);
  }

  @Override
  public void 
  removeListener
  ( ILabelProviderListener arg0 ) 
  {
    listeners.remove(arg0);
  }
}