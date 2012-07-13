package partitioner_configuration.views;

import java.beans.PropertyChangeEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;
import org.eclipse.ui.forms.widgets.TableWrapLayout;
import org.eclipse.ui.part.ViewPart;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import ca.ubc.magic.profiler.dist.model.execution.ExecutionFactory.ExecutionCostType;
import ca.ubc.magic.profiler.dist.model.interaction.InteractionFactory;
import ca.ubc.magic.profiler.dist.model.interaction.InteractionFactory.InteractionCostType;
import ca.ubc.magic.profiler.dist.transform.ModuleCoarsenerFactory;
import ca.ubc.magic.profiler.dist.transform.ModuleCoarsenerFactory.ModuleCoarsenerType;
import ca.ubc.magic.profiler.partitioning.control.alg.PartitionerFactory;
import ca.ubc.magic.profiler.partitioning.control.alg.PartitionerFactory.PartitionerType;

import partitioner.models.PartitionerModel;
import partitioner.views.ModelCreationEditor;
import plugin.Constants;
import plugin.mvc.IController;

import snapshots.views.IView;
import snapshots.views.VirtualModelFileInput;

import org.eclipse.swt.widgets.Control;
import java.util.List;

public class 
PartitionerConfigurationView 
extends ViewPart
implements IView
{
	private FormToolkit toolkit;
	
	private Section 	actions_composite;
	private Label 		profiler_trace_text;

	private Text 		module_exposer_text;
	private Text 		host_config_text;
	
	private Button 		mod_exposer_browse_button;
	private Button 		host_config_browse;
	
	private Button 		exposure_button;
	private Button 		synthetic_node_button;
	
	private IController controller;
	private Combo 		set_coarsener_combo;
	private Object 		controller_switch_lock = new Object();

	private PartitionerWidgets partitioner_widgets;
	
	@Override
	public void 
	createPartControl
	( Composite parent ) 
	{
		this.initialize_active_editor_configuration();
				
		this.toolkit 
			= new FormToolkit( parent.getDisplay() );
				
		ScrolledForm sf 
			= this.toolkit.createScrolledForm(parent);
		
		TableWrapLayout layout 
			= new TableWrapLayout();
		layout.numColumns = 1;
		sf.getBody().setLayout( layout );
		sf.setText("Configure the Model");
		
		Section set_paths_composite
			= this.toolkit.createSection(
				sf.getBody(),
				Section.TITLE_BAR 
					| Section.EXPANDED 
					| Section.DESCRIPTION 
					| Section.TWISTIE
			);
		set_paths_composite.setText("Set the File Paths");
		set_paths_composite.setDescription(
			"Set the files from which the model shall be built."
		);
		set_paths_composite.setSize( new Point(400,400) );
		
		Composite set_paths_client
			= this.toolkit.createComposite( set_paths_composite, SWT.WRAP);
		this.initialize_set_bar_paths_grid( set_paths_client );
		this.initialize_set_paths_bar_widgets( set_paths_client, this.toolkit );
		
		this.toolkit.paintBordersFor(set_paths_client);

		TableWrapData td 
			= new TableWrapData(TableWrapData.FILL);
		set_paths_composite.setLayoutData(td);
		
		set_paths_composite.setClient(set_paths_client);
		
		Section configure_composite
			= this.toolkit.createSection(
				sf.getBody(),
				Section.TITLE_BAR 
					|Section.EXPANDED 
					| Section.DESCRIPTION 
					| Section.TWISTIE
			);
		configure_composite.setText("Configure");
		configure_composite.setDescription(
			"Configure the settings for the model."
		);
		
		Composite configure_client
			= this.toolkit.createComposite(configure_composite);
		this.initialize_configuration_grid(configure_client);
		this.initialize_configuration_widgets( configure_client, this.toolkit );
		td 
			= new TableWrapData(TableWrapData.FILL);
		configure_composite.setLayoutData(td);
		configure_composite.setClient(configure_client);
		
		Section partitioning_composite
			= this.toolkit.createSection(
				sf.getBody(),
				Section.TITLE_BAR 
					|Section.EXPANDED 
					| Section.DESCRIPTION 
					| Section.TWISTIE
			);
		partitioning_composite.setText("Partition");
		partitioning_composite.setDescription(
			"Configure the cost model and the partitioning algorithm."
		);
		
		Composite partitioning_client
			= this.toolkit.createComposite(
				partitioning_composite
			);
		this.initialize_partitioning_grid(partitioning_client);
		this.initialize_partitioning_widgets( partitioning_client, this.toolkit );
		td 
			= new TableWrapData(TableWrapData.FILL);
		partitioning_composite.setLayoutData(td);
		partitioning_composite.setClient(partitioning_client);
		
		this.actions_composite
			= this.toolkit.createSection(
				sf.getBody(),
				Section.TITLE_BAR 
					| Section.EXPANDED 
					| Section.DESCRIPTION
					| Section.TWISTIE
			);
		this.actions_composite.setText("Activate");
		this.actions_composite.setDescription(
			"Activate the model"
		);
		
		Composite actions_client
			= this.toolkit.createComposite(
				this.actions_composite
			);
		td 
			= new TableWrapData(TableWrapData.FILL);
		this.actions_composite.setLayoutData(td);
		this.actions_composite.setClient(actions_client);	
		
		this.initialize_actions_grid(actions_client);
		this.initialize_actions_widgets(actions_client, this.toolkit);
		
		this.set_configuration_widgets_enabled( false );
		this.partitioner_widgets
			.set_partitioning_widgets_enabled( false ); 
	}
	
	private void 
	initialize_active_editor_configuration() 
	{
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
					if( display.getThread() == Thread.currentThread() ){
						IController controller 
							= (IController) event.getProperty("ACTIVE_EDITOR");
						PartitionerConfigurationView.this.setDisplayValues( controller );
					}
					else {
						display.syncExec( 
							new Runnable() {
								public void 
								run()
								{
									IController controller 
										= (IController) event.getProperty("ACTIVE_EDITOR");
									PartitionerConfigurationView.this.setDisplayValues(controller);
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

	protected void 
	setDisplayValues
	( IController controller ) 
	// this function should only be called from swt thread,
	// again though, we want to be concerned about threading
	// issues
	//
	// problem: whenever we change anything we have to send a message
	// to update the model; this basically means we need the controller
	{
		synchronized(this.controller_switch_lock){
			assert controller != null : "The controller argument should not be null";
			
			if(this.controller != null){
				this.controller.removeView(this);
				// this will need to be controlled by a lock
			}
			this.controller = controller;
			this.controller.addView(this);
			
			String[] args 
				= new String[]{
					PartitionerModel.GUI_PROFILER_TRACE,
					PartitionerModel.GUI_MODULE_EXPOSER,
					PartitionerModel.GUI_HOST_CONFIGURATION,
					PartitionerModel.GUI_MODULE_COARSENER,
				
					PartitionerModel.GUI_SET_MODULE_EXPOSURE,
					PartitionerModel.GUI_SET_SYNTHETIC_NODE,
				
					PartitionerModel.GUI_PERFORM_PARTITIONING,
					PartitionerModel.GUI_EXECUTION_COST,
					PartitionerModel.GUI_INTERACTION_COST,
					PartitionerModel.GUI_PARTITIONER_TYPE,
					PartitionerModel.DISABLE_CONFIGURATION_PANEL,
				
					PartitionerModel.GUI_ACTIVATE_HOST_COST_FILTER,
					PartitionerModel.GUI_ACTIVATE_INTERACTION_COST_FILTER,
				};
			
			final Map<String, Object> properties 
				= this.controller.requestProperties(args);
			
			Display.getDefault().asyncExec(
				new Runnable(){
				@Override
				public void
				run()
				{
					PartitionerConfigurationView.this.profiler_trace_text.setText( 
						(String) properties.get(PartitionerModel.GUI_PROFILER_TRACE)
					);
					PartitionerConfigurationView.this.profiler_trace_text.setSize(
						400, 
						PartitionerConfigurationView.this.profiler_trace_text.getSize().y
					);
					
					PartitionerConfigurationView.this.module_exposer_text.setText( 
						(String) properties.get(PartitionerModel.GUI_MODULE_EXPOSER)
					);
					PartitionerConfigurationView.this.host_config_text.setText( 
						(String) properties.get(PartitionerModel.GUI_HOST_CONFIGURATION)
					);
					
					int index 
						= PartitionerConfigurationView.this.findIndex(
							PartitionerConfigurationView.this.set_coarsener_combo, 
							((ModuleCoarsenerType) properties.get(
								PartitionerModel.GUI_MODULE_COARSENER
							)).getText()
						);
					PartitionerConfigurationView.this.set_coarsener_combo.select( index);
					
					PartitionerConfigurationView.this.exposure_button.setSelection( 
						(Boolean) properties.get(PartitionerModel.GUI_SET_MODULE_EXPOSURE)
						);
					PartitionerConfigurationView.this.synthetic_node_button.setSelection( 
						(Boolean) properties.get(PartitionerModel.GUI_SET_SYNTHETIC_NODE)
					);
					
					PartitionerConfigurationView.this
						.partitioner_widgets.setDisplayValues( properties );
					
					PartitionerConfigurationView.this
						.set_configuration_widgets_enabled(
							(Boolean) properties.get(PartitionerModel.DISABLE_CONFIGURATION_PANEL)
						);
				}
			});
		}
	}

	private int 
	findIndex
	( Combo set_coarsener_combo, String string ) 
	{
		String[] items
			= set_coarsener_combo.getItems();
		for( int i = 0; i < items.length; ++i ){
			if( items[i].equals(string)){
				return i;
			}
		}
		throw new IllegalArgumentException(
			"The string is not contained in the combo box."
		);
	}
	
	private void 
	initialize_set_bar_paths_grid
	( Composite parent ) 
	{
		final GridLayout model_configuration_page_grid_layout
			= new GridLayout();
		model_configuration_page_grid_layout.numColumns = 3;
		parent.setLayout( model_configuration_page_grid_layout );
	}
	
	private void 
	initialize_set_paths_bar_widgets
	( Composite parent, FormToolkit toolkit  ) 
	{
		toolkit.createLabel(parent, "Profiler Trace XML: " );
		
		this.profiler_trace_text 
			= toolkit.createLabel( 
				parent, 
				""
			);
		toolkit.createLabel(parent, "");
		
		toolkit.createLabel(parent, "Mod Exposer XML: " );
		this.module_exposer_text 
			= toolkit.createText(parent, "");
		this.module_exposer_text.setEditable( true );
		this.module_exposer_text.setSize( 
			150, 
			this.module_exposer_text.getSize().y
		);
		GridData grid_data 
			= new GridData( SWT.FILL, SWT.FILL, true, false, 1, 1);
		
		grid_data.grabExcessHorizontalSpace = true;
		// hack: will need to fix
		grid_data.widthHint = 600;
		this.module_exposer_text.setLayoutData(grid_data);
		
		this.mod_exposer_browse_button 
			= toolkit.createButton( parent, "Browse", SWT.PUSH );
		this.mod_exposer_browse_button.addSelectionListener( 
			new SelectionAdapter(){
				public void widgetSelected
				( SelectionEvent event )
				{
					FileDialog file_dialog 
						= new FileDialog( 
							PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow().getShell(), 
							SWT.OPEN
						);
					file_dialog.setText("Select File");
					file_dialog.setFilterPath( 
						PartitionerConfigurationView.this
							.profiler_trace_text.getText() 
					);
					String selected
						= file_dialog.open();
					if(selected != null){
						PartitionerConfigurationView.this
							.module_exposer_text.setText(selected);
						
						PartitionerConfigurationView.this.controller
							.setModelProperty( 
								PartitionerModel.GUI_MODULE_EXPOSER, 
								selected 
							);
					}
				}
			}
		);
		
		toolkit.createLabel(parent, "Host Config. XML: " );
		this.host_config_text
			=  toolkit.createText(parent,"");
		this.host_config_text.setEditable( true );
		grid_data 
			= new GridData( SWT.FILL, SWT.FILL, true, false, 1, 1);
		
		grid_data.grabExcessHorizontalSpace = true;
		// hack: will need to fix
		grid_data.widthHint = 600;
		this.host_config_text.setLayoutData(grid_data);
		
		this.host_config_browse 
			= toolkit.createButton(parent, "Browse", SWT.PUSH);
		
		this.host_config_browse.addSelectionListener( new SelectionAdapter(){
			public void widgetSelected( SelectionEvent event ){
				FileDialog file_dialog 
					= new FileDialog( 
						PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getShell(), 
						SWT.OPEN
					);
				file_dialog.setText("Select File");
				file_dialog.setFilterPath( profiler_trace_text.getText() );
				String selected
					= file_dialog.open();
				if(selected != null){
					PartitionerConfigurationView.this
						.host_config_text.setText(selected);
					
					PartitionerConfigurationView.this.controller
						.setModelProperty( 
							PartitionerModel.GUI_HOST_CONFIGURATION, 
							selected 
						);
				}
			}
		});
	}
	
	private void 
	initialize_configuration_grid
	( Composite parent ) 
	{
		final GridLayout model_configuration_page_grid_layout
			= new GridLayout();
		model_configuration_page_grid_layout.numColumns 
			= 2;
		parent.setLayout( model_configuration_page_grid_layout );
	}
	
	private void 
	initialize_configuration_widgets
	( Composite parent, FormToolkit toolkit ) 
	{
		toolkit.createLabel(parent, "Coarsener: " );

		this.initialize_coarsener_combo_box(parent);
		
		this.exposure_button
			= toolkit.createButton(
				parent, 
				"Activate Module Exposure", 
				SWT.CHECK
			);
		GridData grid_data 
			= new GridData( SWT.BEGINNING, SWT.FILL, false, false );
		grid_data.horizontalSpan = 1;
		this.exposure_button.setLayoutData(grid_data);
		
		this.exposure_button.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected
				( SelectionEvent e )
				{
					PartitionerConfigurationView.this.controller.setModelProperty(
						PartitionerModel.GUI_SET_MODULE_EXPOSURE, 
						new Boolean(
							PartitionerConfigurationView.this
								.exposure_button.getSelection()
							)
					);
				}
			}
		);
		
		this.createDummyLabel(parent, toolkit);
		
		this.synthetic_node_button
			= toolkit.createButton(parent, "Add Synthetic Node", SWT.CHECK);
		grid_data 
			= new GridData(SWT.BEGINNING, SWT.FILL, false, false);
		grid_data.horizontalSpan = 1;
		synthetic_node_button.setLayoutData( grid_data );
		
		synthetic_node_button.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected
				( SelectionEvent e )
				{
					PartitionerConfigurationView.this.controller.setModelProperty(
						PartitionerModel.GUI_SET_SYNTHETIC_NODE,
						new Boolean(synthetic_node_button.getSelection())
					);
				}
			}
		);
	}
	
	private Label 
	createDummyLabel
	( Composite parent, FormToolkit toolkit ) 
	// this function must execute in the SWT thread
	{
		Label return_value = null;
		
		if( toolkit != null ){
			return_value
				= toolkit.createLabel(parent, "", SWT.NONE);
			GridData grid_data 
				= new GridData(SWT.BEGINNING, SWT.FILL, false, false);
			grid_data.horizontalSpan = 1;
			return_value.setLayoutData( grid_data );
		}
		
		return return_value;
	}
	
	private void
	initialize_coarsener_combo_box
	( Composite parent ) 
	{
		this.set_coarsener_combo
			= new Combo(parent, SWT.NONE);
		
        for( final ModuleCoarsenerType mcType 
        		: ModuleCoarsenerFactory.ModuleCoarsenerType.values())
        {
            this.set_coarsener_combo.add(mcType.getText());
        }
		
		this.set_coarsener_combo.addSelectionListener( 
			new SelectionAdapter(){
				public void 
				widgetSelected( SelectionEvent se )
				{
					PartitionerConfigurationView.this.controller.setModelProperty(
						PartitionerModel.GUI_MODULE_COARSENER,
						ModuleCoarsenerType.fromString(
							PartitionerConfigurationView.this
								.set_coarsener_combo.getText()
						)
					);
				}
			}
		);
		
		this.set_coarsener_combo.select(0);
	}
	
	private void 
	initialize_partitioning_grid
	( Composite parent ) 
	{
		final GridLayout model_configuration_page_grid_layout
			= new GridLayout();
		model_configuration_page_grid_layout.numColumns 
			= 2;
		parent.setLayout( model_configuration_page_grid_layout );
	}

	private void 
	initialize_partitioning_widgets
	( Composite parent, FormToolkit toolkit ) 
	{
		this.partitioner_widgets
			= new PartitionerWidgets( parent, toolkit);
	}
	
	private void 
	initialize_actions_grid
	( Composite parent ) 
	{
		final GridLayout model_configuration_page_grid_layout
			= new GridLayout();
		model_configuration_page_grid_layout.numColumns 
			= 2;
		parent.setLayout( model_configuration_page_grid_layout );
	}
	
	private void 
	initialize_actions_widgets
	( Composite parent, FormToolkit toolkit ) 
	{
		final Button generate_model
			= toolkit.createButton(
				parent, 
				"Generate Model", 
				SWT.PUSH
			);
		GridData grid_data 
			= new GridData( SWT.BEGINNING, SWT.FILL, false, false);
		grid_data.horizontalSpan = 1;
		generate_model.setLayoutData( grid_data );
		
		generate_model.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected
				( SelectionEvent e )
				{
					PartitionerConfigurationView.this.controller.setModelProperty(
							PartitionerModel.GUI_HOST_CONFIGURATION, 
						PartitionerConfigurationView.this.host_config_text.getText()
					);
					PartitionerConfigurationView.this.controller.setModelProperty(
							PartitionerModel.GUI_MODULE_EXPOSER, 
						PartitionerConfigurationView.this.module_exposer_text.getText()
					);
					
					// generate a model, and if partitioning is set, also 
					// initialize the test framework
					PartitionerConfigurationView.this.controller.notifyModel(
						Constants.GENERATE_MODEL_EVENT
					);
				}
			}
		);
	}
	
	@Override
	public void 
	modelPropertyChange
	( final PropertyChangeEvent evt ) 
	{
		System.err.println(
			"Event generated in PartitionerConfigurationView: " 
			+ evt.getPropertyName()
		);
		Display display = Display.getCurrent();
		
		if(display == null){
			System.err.println("Uh oh null display");
			display = Display.getDefault();
		}
		display.syncExec( 
			new Runnable()
			{
				@Override
				public void
				run()
				{
					switch(evt.getPropertyName())
					{
					case PartitionerModel.GUI_PROFILER_TRACE:
						PartitionerConfigurationView.this.profiler_trace_text.setText( 
							(String) evt.getNewValue() 
						);
						break;
					case PartitionerModel.GUI_MODULE_EXPOSER:
						PartitionerConfigurationView.this.module_exposer_text.setText( 
							(String) evt.getNewValue() 
						);
						break;
					case PartitionerModel.GUI_HOST_CONFIGURATION:
						PartitionerConfigurationView.this.host_config_text.setText( 
							(String) evt.getNewValue() 
						);
						break; 
					case PartitionerModel.GUI_PERFORM_PARTITIONING:
					{
						Boolean enabled 
							= (Boolean) evt.getNewValue();
						PartitionerConfigurationView.this
							.partitioner_widgets
							.set_partitioning_widgets_enabled( enabled );
						break; 
					}
					case PartitionerModel.GUI_ACTIVATE_HOST_COST_FILTER:
					{
						Boolean enabled 
							= (Boolean) evt.getNewValue();
						PartitionerConfigurationView.this
							.partitioner_widgets.activate_host_filter_button
							.setSelection(enabled);
						break;
					}
					case PartitionerModel.GUI_ACTIVATE_INTERACTION_COST_FILTER:
					{
						Boolean enabled 
							= (Boolean) evt.getNewValue();
						PartitionerConfigurationView.this
							.partitioner_widgets.activate_interaction_filter_button
							.setSelection(enabled);
						break;
					}
					case PartitionerModel.DISABLE_CONFIGURATION_PANEL:
					{
						boolean enabled
							= (boolean) evt.getNewValue();
						PartitionerConfigurationView.this
							.set_configuration_widgets_enabled( enabled );
						PartitionerConfigurationView.this
							.partitioner_widgets
							.set_partitioning_widgets_enabled( enabled );
						PartitionerConfigurationView.this
							.updateModelName();
						break;
					}
					case PartitionerModel.EDITOR_CLOSED:
						PartitionerConfigurationView.this
							.clear_all_entries();
						PartitionerConfigurationView.this
							.set_configuration_widgets_enabled( false );
						PartitionerConfigurationView.this
							.partitioner_widgets
							.set_partitioning_widgets_enabled( false );
						break;
					default:
						System.out.println("Swallowing message.");
					};
				}
			}
		);
	
	}

	private void 
	clear_all_entries() 
	{
		// TODO the following doesn't actually work
		// 		you must figure out why
		System.err.println("Clearing all entries");
		
		synchronized(this.controller_switch_lock){
				this.controller.removeView(this);
				this.controller 
					= null;
			}
		
		this.profiler_trace_text.setText("  ");
		this.module_exposer_text.setText("");
		this.host_config_text.setText("");
		
		this.exposure_button.setSelection(false);
		this.synthetic_node_button.setSelection(false);
		
		this.partitioner_widgets.clear_selections();
		
		Display.getDefault().update();
		this.getViewSite().getShell().layout();
		this.getViewSite().getShell().update();
	}

	public void 
	set_configuration_widgets_enabled
	( final boolean enabled ) 
	{
		this.host_config_text.setEditable(enabled);
		this.module_exposer_text.setEditable(enabled);
		this.actions_composite.setVisible(enabled);
		
		this.synthetic_node_button.setEnabled(enabled);
		this.exposure_button.setEnabled(enabled);
		
		this.mod_exposer_browse_button.setVisible(enabled);
		this.host_config_browse.setVisible(enabled);
		this.set_coarsener_combo.setEnabled(enabled);
		
		this.partitioner_widgets.enablePartitioning( enabled );
	}
	
	private void 
	updateModelName() 
	{
		Display.getDefault().asyncExec( 
			new Runnable(){
				@Override
				public void 
				run(){
					String name_suffix
						= new SimpleDateFormat("HH:mm:ss")
							.format( new Date() );
					String coarsener
						= PartitionerConfigurationView.this.set_coarsener_combo.getText();
					String new_name
						= coarsener + "_" + name_suffix;
					
					ModelCreationEditor page 
						= (ModelCreationEditor) 
							PartitionerConfigurationView.this
								.getSite().getPage().getActiveEditor();
					assert page instanceof ModelCreationEditor 
						: "Uh oh. We need to pass the editor reference as an argument.";
					VirtualModelFileInput input
						= (VirtualModelFileInput) page.getEditorInput();
						
					
					input.setSecondaryName(new_name);
					
					BundleContext context 
						= FrameworkUtil.getBundle(
							PartitionerConfigurationView.class
						).getBundleContext();
			        ServiceReference<EventAdmin> ref 
			        	= context.getServiceReference(EventAdmin.class);
			        EventAdmin eventAdmin 
			        	= context.getService(ref);
			        Map<String,Object> properties 
			        	= new HashMap<String, Object>();
			        properties.put("REFRESH", new Boolean(true));
			        Event event 
			        	= new Event("viewcommunication/syncEvent", properties);
			        eventAdmin.sendEvent(event);
			        event = new Event("viewcommunication/asyncEvent", properties);
			        eventAdmin.postEvent(event);
			        
			        page.updateTitle();
				}
			}
		);
	}

	@Override
	public void setFocus() {}
	
	////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////
	
	private class
	PartitionerWidgets
	// the following class exists solely to group together all of the 
	// partitioner functionality; this should make it easier to tell
	// where to make change and additions whenever new widgets are added
	{
		private Button		perform_partitioning_button;
		private Combo 		partitioning_algorithm_combo;
		private Combo 		interaction_model_combo;
		private Combo 		execution_model_combo;
		private Button 		activate_host_filter_button;
		private Button 		activate_interaction_filter_button;

		private List<Control> activate_and_deactivate_partitioner_list
			= new ArrayList<Control>(10);
		
		PartitionerWidgets
		( Composite parent, FormToolkit toolkit)
		// any widgets added after the fact must be added to the
		// partitioner list, and must be queried for when in the
		// setDisplay function above; there should also be a callback
		// in the switch statement in case its state changes
		{
			this.perform_partitioning_button
				= toolkit.createButton(
					parent, 
					"Perform Partitioning", 
					SWT.CHECK
				);
			GridData grid_data 
				= new GridData( SWT.BEGINNING, SWT.FILL, false, false );
			grid_data.horizontalSpan 
				= 1;
			this.perform_partitioning_button.setLayoutData(grid_data);
			
			this.perform_partitioning_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected
					( SelectionEvent e )
					{
						PartitionerConfigurationView.this.controller.setModelProperty(
								PartitionerModel.GUI_PERFORM_PARTITIONING, 
							new Boolean(
								PartitionerWidgets.this
									.perform_partitioning_button.getSelection()
							)
						);
					}
				}
			);
			
			PartitionerConfigurationView.this
				.createDummyLabel(parent, toolkit);
			
			this.activate_host_filter_button
				= toolkit.createButton(
					parent, 
					"Activate Host Cost Filter", 
					SWT.CHECK
				);
			grid_data 
				= new GridData(
					SWT.BEGINNING, 
					SWT.FILL, 
					false, false
				);
			grid_data.horizontalSpan = 1;
			this.activate_host_filter_button.setLayoutData( grid_data );
			
			this.activate_host_filter_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected
					( SelectionEvent e )
					{
						PartitionerConfigurationView.this
							.controller.setModelProperty(
								PartitionerModel.GUI_ACTIVATE_HOST_COST_FILTER,
								new Boolean(
									PartitionerWidgets.this
										.activate_host_filter_button.getSelection()
								)
							);
					}
				}
			);
			PartitionerConfigurationView.this
				.createDummyLabel( parent, toolkit );
			
			this.activate_interaction_filter_button
				= toolkit.createButton(
					parent, 
					"Activate Interaction Cost Filter", 
					SWT.CHECK
				);
			grid_data 
				= new GridData(
					SWT.BEGINNING, 
					SWT.FILL, 
					false, false
				);
			grid_data.horizontalSpan = 1;
			this.activate_interaction_filter_button
				.setLayoutData( grid_data );
			
			this.activate_interaction_filter_button.addSelectionListener(
				new SelectionAdapter()
				{
					@Override
					public void
					widgetSelected
					( SelectionEvent e )
					{
						PartitionerConfigurationView.this.controller.setModelProperty(
							PartitionerModel.GUI_ACTIVATE_INTERACTION_COST_FILTER,
							new Boolean(
								PartitionerWidgets.this
									.activate_interaction_filter_button
									.getSelection()
							)
						);
					}
				}
			);
			PartitionerConfigurationView.this
				.createDummyLabel( parent, toolkit );
		
			toolkit.createLabel( parent, "Execution Cost Model: " );
			this.initialize_execution_model_combo_box(parent);
			
			toolkit.createLabel( parent, "Interaction Cost Model: " );
			this.initialize_interaction_model_combo_box(parent);
			
			toolkit.createLabel( parent, "Partitioning Algorithm" );
			this.initilize_partitioning_algorithm_combo_box(parent);
			
			// all widgets defined for this frame except the
			// activate partitioner widget must be added to this list
			this.initialize_partitioner_controls_list(
				this.activate_host_filter_button,
				this.activate_interaction_filter_button,
				this.partitioning_algorithm_combo,
				this.interaction_model_combo,
				this.execution_model_combo
			);
		}
		
		private void 
		initialize_partitioner_controls_list
		( Control... controls)
		{
			for(Control control : controls){
				this.activate_and_deactivate_partitioner_list.add(
					control
				);
			}
		}

		public void 
		setDisplayValues
		(	Map<String, Object> map ) 	
		{
			boolean perform_partitioning
				= (Boolean) map.get(
						PartitionerModel.GUI_PERFORM_PARTITIONING
				);
			ExecutionCostType execution_cost_type
				= (ExecutionCostType) map.get(
						PartitionerModel.GUI_EXECUTION_COST
				);
			InteractionCostType interaction_cost_type
				= (InteractionCostType) map.get(
						PartitionerModel.GUI_INTERACTION_COST
				);
			PartitionerType partitioner_type
				= (PartitionerType) map.get(
						PartitionerModel.GUI_PARTITIONER_TYPE
				);
			boolean activate_host_cost_filter
				= (Boolean) map.get(
						PartitionerModel.GUI_ACTIVATE_HOST_COST_FILTER
				);
			boolean activate_interaction_cost_filter
				= (Boolean) map.get(
						PartitionerModel.GUI_ACTIVATE_INTERACTION_COST_FILTER
				);
			int index;
			
			
			this.perform_partitioning_button
				.setSelection( perform_partitioning );
			this.activate_host_filter_button
				.setSelection( activate_host_cost_filter );
			this.activate_interaction_filter_button
				.setSelection( activate_interaction_cost_filter );
			
			index 
				= PartitionerConfigurationView.this.findIndex(
					this.execution_model_combo, 
					execution_cost_type.getText()
				);
			this.execution_model_combo.select( index );
			
			index 
				= PartitionerConfigurationView.this.findIndex(
					this.interaction_model_combo, 
					interaction_cost_type.getText()
				);
			this.interaction_model_combo.select( index );
			index 
				= PartitionerConfigurationView.this.findIndex(
					this.partitioning_algorithm_combo, 
					partitioner_type.getText()
				);
			
			this.partitioning_algorithm_combo.select( index ); 
		}

		public void 
		enablePartitioning
		( boolean enabled ) 
		{
			this.perform_partitioning_button.setEnabled(enabled);
		}

		public void 
		clear_selections() 
		{
			this.activate_host_filter_button.setSelection(false);
			this.activate_interaction_filter_button.setSelection(false);
			this.perform_partitioning_button.setSelection(false);
		}

		private void 
		initilize_partitioning_algorithm_combo_box
		( Composite parent ) 
		{
			this.partitioning_algorithm_combo
				= new Combo(parent, SWT.NONE);
			
		    for( final PartitionerType partitioner_type 
		    		: PartitionerFactory.PartitionerType.values())
		    {
		    	this.partitioning_algorithm_combo.add(
		    		partitioner_type.getText()
		    	);
		    }
			
		    this.partitioning_algorithm_combo.addSelectionListener( 
				new SelectionAdapter(){
					public void 
					widgetSelected( SelectionEvent se )
					{
						System.out.println("Selected partitioner");
						PartitionerConfigurationView.this.controller.setModelProperty(
								PartitionerModel.GUI_PARTITIONER_TYPE,
							PartitionerType.fromString(
								PartitionerWidgets.this.
									partitioning_algorithm_combo.getText()
							)
						);
					}
				}
			);
			
		    this.partitioning_algorithm_combo.select(0);
		}
		
		private void 
		initialize_interaction_model_combo_box
		( Composite parent ) 
		{
			this.interaction_model_combo
				= new Combo(parent, SWT.NONE);
			
		    for( final InteractionCostType interaction_cost_type 
		    		: InteractionFactory.InteractionCostType.values())
		    {
		    	this.interaction_model_combo.add(
		    		interaction_cost_type.getText()
		    	);
		    }
			
		    this.interaction_model_combo.addSelectionListener( 
				new SelectionAdapter(){
					public void 
					widgetSelected( SelectionEvent se )
					{
						PartitionerConfigurationView.this.controller.setModelProperty(
								PartitionerModel.GUI_INTERACTION_COST,
							InteractionCostType.fromString(
								PartitionerWidgets.this
								.interaction_model_combo.getText()
							)
						);
					}
				}
			);
		
		    this.interaction_model_combo.select(0);
		}
		
		private void 
		initialize_execution_model_combo_box
		( Composite parent ) 
		{
			this.execution_model_combo
				= new Combo(parent, SWT.NONE);
			
		    for( final ExecutionCostType execution_cost_type 
		    		: ExecutionCostType.values())
		    {
		    	this.execution_model_combo.add(
		    		execution_cost_type.getText()
		    	);
		    }
			
		    this.execution_model_combo.addSelectionListener( 
				new SelectionAdapter(){
					public void 
					widgetSelected( SelectionEvent se )
					{
						PartitionerConfigurationView.this.controller.setModelProperty(
								PartitionerModel.GUI_EXECUTION_COST,
							ExecutionCostType.fromString(
								PartitionerWidgets.this
									.execution_model_combo.getText()
							)
						);
					}
				}
			);
		
		    this.execution_model_combo.select(0);
		}
		
		void
		set_partitioning_widgets_enabled
		( boolean enabled )
		// this function must execute in the SWT thread!!!
		{
			for(Control partitioning_control : this.activate_and_deactivate_partitioner_list){
				partitioning_control.setEnabled(enabled);
			}
		}
	}
}