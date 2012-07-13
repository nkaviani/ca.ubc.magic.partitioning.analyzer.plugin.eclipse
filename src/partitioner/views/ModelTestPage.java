package partitioner.views;

// TODO: get the table up and running and modifiable
// TODO: add the context menu
// TODO: add the functionality bit by bit
//		hopefully finish the test framework by the end of the day
//		hopefully work on bugs tomorrow, documentation the next week
//		start writing final report and testing

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.core.databinding.observable.list.ObservableList;
import org.eclipse.core.databinding.observable.list.WritableList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapLayout;

import partitioner.models.TestFrameworkModel;
import plugin.Constants;
import plugin.mvc.ControllerDelegate;
import plugin.mvc.IController;
import plugin.mvc.IModel;
import snapshots.views.IView;
import ca.ubc.magic.profiler.dist.model.DistributionModel;
import ca.ubc.magic.profiler.dist.model.HostModel;
import ca.ubc.magic.profiler.dist.model.ModuleModel;
import ca.ubc.magic.profiler.dist.model.report.ReportModel;
import ca.ubc.magic.profiler.simulator.control.SimulatorFactory;
import ca.ubc.magic.profiler.simulator.control.SimulatorFactory.SimulatorType;
import ca.ubc.magic.profiler.simulator.framework.IFrameworkListener;
import ca.ubc.magic.profiler.simulator.framework.SimulationFramework;
import ca.ubc.magic.profiler.simulator.framework.SimulationUnit;

// an interesting thing about the modeltestpage is that it should only
// be active when the partitioning has completed
// that means it will only have a proper model to communicate with
// after the partitioning has been completed
public class 
ModelTestPage 
extends ScrolledForm
implements IFrameworkListener,
	IView
{
	private Combo 					simulation_type_combo;
	private Label 					best_run_name_label;
	private Label 					best_run_algorithm_label;
	private Label 					best_run_cost_label;
	private Label 					total_simulation_units;
	
	private Section 				table_composite;
	private Section 				control_composite;
	
	private TableViewer				simulations_table_viewer;
	private ObservableList			table_input;
	
	private Integer 				current_id = 0;
	
	private Table 					table;
	private String[] 				column_names;
	private IController				test_framework_controller;
	
	// any method beginning with the term initialize are called
	// only from the constructor and may be assumed to executed on
	// the SWT event thread
	public ModelTestPage
	( 	FormToolkit toolkit, 
		Composite parent, 
		ModelCreationEditor model_creation_editor,
		TestFrameworkModel test_framework_model ) 
	{
		super( parent );
		
		this.activate( test_framework_model );
		this.initialize_page_properties( toolkit );
		
		this.control_composite
			= toolkit.createSection(
				this.getBody(),
				Section.TITLE_BAR 
					| Section.EXPANDED 
					| Section.DESCRIPTION 
					| Section.TWISTIE
			);
		this.control_composite.setText("Run a Simulation");
				
		Composite control_client
			= toolkit.createComposite( this.control_composite );

		this.initializeControlGrid( control_client );
		this.initializeControlWidgets( control_client, toolkit );
		
		this.control_composite.setClient(control_client);
	
		this.table_composite
			= toolkit.createSection(
					this.getBody(),
					Section.TITLE_BAR 
						| Section.EXPANDED 
						| Section.DESCRIPTION 
						| Section.TWISTIE
				);
		this.table_composite.setText("View Simulations");
				
		Composite table_client
			= toolkit.createComposite(this.table_composite);
		this.initialize_table_client_grid(table_client);
		this.initialize_table_client_widgets( table_client );
		
		// if we want to set the size of each column relative to the parent
		// or the table, we have to wait until the table is drawn or resized
		// to get a value; if we simply call getClientArea() in the constructor
		// we will get a value of 0,0
		parent.addControlListener( 
			new TestPageResizedAdapter(
				parent, this.table, this.column_names, 
				this.table_composite, this.control_composite 
			) 
		);
		this.table_composite.setClient(table_client);
		
		this.initialize_context_menu(model_creation_editor);
	}
	
	/// for now we call an activate function
	// later we may simply wait to create the page until later
	// and have everything happen in the constructor
	private void
	activate
	( IModel test_framework_model )
	{
		this.test_framework_controller
			= new ControllerDelegate();
		this.test_framework_controller.addView(this);
		this.test_framework_controller.addModel(
			test_framework_model
		);
		
		this.initialize_simulation_framework();
		
		assert this.test_framework_controller != null 
			: "The test framework controller must be initialize here";
	}
	
	private void 
	initialize_simulation_framework() 
	{
		Map<String, Object> map 
			= this.test_framework_controller.requestProperties(
				new String[] {
					TestFrameworkModel.TEST_SIMULATION_FRAMEWORK,
				}
			);
		SimulationFramework simulation_framework
			= (SimulationFramework) map.get(
				TestFrameworkModel.TEST_SIMULATION_FRAMEWORK
			);
		simulation_framework.addFrameworkListener(
			(IFrameworkListener) this
		);
	}
	
	private void 
	initialize_page_properties
	( FormToolkit toolkit) 
	{
		this.setText( "Test and Simulation Framework" );
		TableWrapLayout layout 
			= new TableWrapLayout();
		layout.numColumns = 1;
		this.getBody().setLayout(layout);
	}

	private void 
	initializeControlGrid
	( Composite parent ) 
	{
		final GridLayout test_framework_page_grid_layout
			= new GridLayout();
		test_framework_page_grid_layout.numColumns 
			= 3;
		parent.setLayout( test_framework_page_grid_layout );
	}
	
	private void 
	initializeControlWidgets
	( Composite parent, FormToolkit toolkit ) 
	{
		// we do not add a layoutdata to the Label since that screws
		// up the vertical spacing
		toolkit.createLabel(parent, "Simulation Type: " );
		
		this.simulation_type_combo
			= new Combo(parent, SWT.NONE);
		this.initialize_simulation_types_combo(
			parent, 
			this.simulation_type_combo
		);
		
		Button run_simulation_button
			= toolkit.createButton(
				parent, 
				"Run", 
				SWT.PUSH
			);
		GridData grid_data  
			= new GridData( SWT.BEGINNING, SWT.FILL, false, false );
		grid_data.horizontalSpan = 1;
		run_simulation_button.setLayoutData(grid_data);
		
		run_simulation_button.addSelectionListener(
			new SelectionAdapter()
			{
				@Override
				public void
				widgetSelected
				( SelectionEvent e )
				{
					///
					ModelTestPage.this.test_framework_controller.setModelProperty(
						Constants.GUI_SIMULATION_TYPE,
						simulation_type_combo.getText()
					);
					ModelTestPage.this.test_framework_controller.notifyModel(
						Constants.EVENT_RUN_SIMULATION
					);
				}
			}
		);
		
		toolkit.createLabel(parent, "Best Run Name: " );
		this.best_run_name_label
			= toolkit.createLabel(parent, "                                      ");
		grid_data  
			= new GridData( SWT.BEGINNING, SWT.FILL, false, false );
		grid_data.horizontalSpan = 2;
		this.best_run_name_label.setLayoutData(grid_data);
		this.best_run_name_label.setSize(500, this.best_run_name_label.getSize().y);
		
		toolkit.createLabel(parent, "Best Run Algorithm: " );
		this.best_run_algorithm_label
			= toolkit.createLabel(parent, "                                      ");
		grid_data  
			= new GridData( SWT.BEGINNING, SWT.FILL, false, false );
		grid_data.horizontalSpan = 2;
		this.best_run_algorithm_label.setLayoutData(grid_data);
		
		toolkit.createLabel(parent, "Best Run Cost: " );
		this.best_run_cost_label
			= toolkit.createLabel(parent, "                                      ");
		grid_data  
			= new GridData( SWT.BEGINNING, SWT.FILL, false, false );
		grid_data.horizontalSpan = 2;
		this.best_run_cost_label.setLayoutData(grid_data);
	
		toolkit.createLabel(parent, "Total Runs: " );
		this.total_simulation_units
			= toolkit.createLabel(parent, "0");
		grid_data  
			= new GridData( SWT.BEGINNING, SWT.FILL, false, false );
		grid_data.horizontalSpan = 2;
		this.total_simulation_units.setLayoutData(grid_data);
	}
	
	private void 
	initialize_table_client_grid
	( Composite parent ) 
	{
		final GridLayout test_framework_page_grid_layout
			= new GridLayout();
		test_framework_page_grid_layout.numColumns 
			= 1;
		parent.setLayout( test_framework_page_grid_layout );
	}
	
	private void 
	initialize_table_client_widgets
	( final Composite parent ) 
	{
		this.column_names
			= new String[] {
				"ID",
				"Name",
				"Algorithm",
				"Execution Cost",
				"Communication Cost",
				"Total Cost",
			};
		
		List<Object[]> obj 
			= new ArrayList<Object[]>();
		this.table_input 
			= new WritableList( obj, Object[].class);
		
		final int style
			= SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION 
			| SWT.BORDER | SWT.FULL_SELECTION ;
		
		this.table	
		 	= new Table( parent, style );
		
		this.simulations_table_viewer
			= new TableViewer( this.table );
		
		this.simulations_table_viewer.setContentProvider(
			new ObservableListContentProvider()
		);
		
		for(int i = 0; i < column_names.length; ++i )
		{
			final int index = i;
			TableViewerColumn table_viewer_column 
				= new TableViewerColumn( this.simulations_table_viewer, SWT.NONE );
			TableColumn col 
				= table_viewer_column.getColumn();
			
			col.setText( column_names[i]);
			col.setWidth( 750/column_names.length );
			table_viewer_column.setLabelProvider(
				new ColumnLabelProvider() {
					@Override
					public String 
					getText
					( Object element )
					{
						Object[] array 
							= (Object[]) element;
						return array[index].toString();
					}
				}
			);
		}
		this.simulations_table_viewer.setInput( this.table_input );
		this.simulations_table_viewer.refresh();
		
		// solution found in
		// http://www.eclipse.org/forums/index.php/m/521315/
		int desiredHeight 
			= table.getItemHeight() * 20 + table.getHeaderHeight();
		if (parent.getLayout() == null) { 
			table.setSize(750, desiredHeight);
		}  
		else {
			// assumes GridLayout
			GridData grid_data 
				= new GridData( 750, desiredHeight);
			table.setLayoutData(grid_data);
		}
		
		table.setHeaderVisible( true );
		table.setLinesVisible( true );
		
	}
	
	private void
	initialize_simulation_types_combo
	( Composite parent, final Combo simulation_type_combo ) 
	{
		for( SimulatorType type : SimulatorFactory.SimulatorType.values()){
            simulation_type_combo.add(type.getText());
        }
	
	    simulation_type_combo.addSelectionListener( 
			new SelectionAdapter(){
				public void 
				widgetSelected( SelectionEvent se )
				{
					ModelTestPage.this.test_framework_controller.setModelProperty(
						Constants.GUI_SIMULATION_TYPE,
							simulation_type_combo.getText()
					);
				}
			}
		);
	
	    simulation_type_combo.select(0);
	}
	
	public void
	initialize_context_menu
	( ModelCreationEditor root_editor )
	{
		// First we create a menu Manager
		MenuManager menuManager 
			= new MenuManager();
		
		Menu menu 
			= menuManager.createContextMenu(
				this.simulations_table_viewer.getTable()
			);
		
		// Set the MenuManager
		this.simulations_table_viewer.getTable().setMenu(menu);
		
		root_editor.getSite().registerContextMenu(
			menuManager, 
			this.simulations_table_viewer
		);
		
		// Make the selection available
		root_editor.getSite().setSelectionProvider(
			this.simulations_table_viewer
		); 
	}
	
	@Override
	public void 
	modelPropertyChange
	( final java.beans.PropertyChangeEvent evt ) 
	{
		System.err.println("inside modeltestpage modelPropertyChange");
		
		// event may be triggered by a process in a non-SWT thread
		Display.getDefault().asyncExec(
			new Runnable(){
				@Override
				public void run(){
					System.err.println("Received event: " + evt.getPropertyName());
					switch( evt.getPropertyName()){
					case Constants.GUI_SIMULATION_ADDED:
						SimulationUnit unit = (SimulationUnit) evt.getNewValue();
						
						ModelTestPage.this.table_input.add(
							// the first element is the id, I am removing it for now until 
							// I am certain how it is used and what I do to accommodate it
							new Object[]{ 
								ModelTestPage.this.current_id++, 
								// we may have a threading problem, under the debugger
								// the following throws a null pointer exception
								new String(unit.getName()), 
								new String( unit.getAlgorithmName() ), 
								"", "", "..."
							}
						);
						
						Integer total_run
							= Integer.parseInt(ModelTestPage.this.total_simulation_units.getText());
						ModelTestPage.this.total_simulation_units.setText(  Integer.toString(total_run + 1) );
						System.err.println("New total run: " + ModelTestPage.this.total_simulation_units.getText());
						break;
					case Constants.GUI_SIMULATION_REMOVED:
						// take removed value out of the table
						break;
					case Constants.INCREMENT_ID:
						Integer id 
							= (Integer) evt.getNewValue();
						ModelTestPage.this.current_id
							= id;
						break;
					case Constants.BEST_RUN_NAME:
						String best_run_name
							= (String) evt.getNewValue();
						System.out.println("Best run name: " + best_run_name);
						ModelTestPage.this.best_run_name_label.setText(best_run_name);
						break;
					case Constants.BEST_RUN_ALGORITHM:
						String best_run_algorithm
							= (String) evt.getNewValue();
						System.out.println("Best run algorithm: " + best_run_algorithm);
						ModelTestPage.this.best_run_algorithm_label.setText(
							best_run_algorithm
						);
						break;
					case Constants.BEST_RUN_COST:
						String best_run_cost
							= (String) evt.getNewValue();
						System.out.println("Best run cost: " + best_run_cost);
						ModelTestPage.this.best_run_cost_label.setText(
							best_run_cost
						);
						break;
					case Constants.SIMULATION_TABLE_RUN_UPDATE:
						// use the index to find the right table entry
						Object[] obj
							= (Object[]) evt.getNewValue();
						for( int i = 0; i < ModelTestPage.this.table_input.size(); ++i){
							Object[] array
								= (Object[]) ModelTestPage.this.table_input.get(i);
							if(array[0].equals((Integer) obj[0])){
								Object[] modifiable_array
									= (Object[]) ModelTestPage.this.table_input.get(i);
								modifiable_array[3] = (Double) obj[1];
								modifiable_array[4] = (Double) obj[2];
								modifiable_array[5] = (Double) obj[3];
							}
							ModelTestPage.this.simulations_table_viewer.refresh();
						}
						break;
					default:
						System.out.println("Model Test Page swallowed event: " + evt.getPropertyName());
						break;
					}
				}
			}
		);
	}
	
	//////////////////////////////////////////////////////////////////////////////////
	///	Logic for populating the table
	///		1) First: functionality for adding a new entry
	//////////////////////////////////////////////////////////////////////////////////
	
	private SimulationUnitCustomizationNew mSimUnitCustomization;
	
	@Override
	public void 
	simulationAdded
	( SimulationUnit unit ) 
	{
		System.err.println("performing callback");
		
		// update state of gui on the callback
		this.test_framework_controller.setModelProperty(
			Constants.GUI_SIMULATION_ADDED,
			unit
		);
   }

	@Override
	public void 
	simulationRemoved
	( SimulationUnit unit ) 
	{
		this.test_framework_controller.setModelProperty(
			Constants.GUI_SIMULATION_REMOVED, 
			unit
		);
	}

	@Override
	public void 
	updateSimulationReport
	( SimulationUnit unit, ReportModel report ) 
	{    
		this.test_framework_controller.setModelProperty(
			Constants.GUI_UPDATE_SIMULATION_REPORT, 
			new Object[]{ unit, report }
		);
    }
    
    public void 
    updateBestSimReport
    ( SimulationUnit unit )
    {
    	this.test_framework_controller.setModelProperty(
    		Constants.GUI_UPDATE_BEST_SIMULATION_REPORT, 
    		unit
    	);
    }
    
	// TODO the following is something that would traditionally be handled by the
	// controller: the view detects the event, but does not decide how to handle it
	// only how to notify the controller
    public void 
    customSimulationButtonActionPerformed
    ( java.awt.event.ActionEvent evt ) 
    {
    	// for now pass the controller and have the dialog query for
    	// any necessary information...this will introduce a necessary
    	// split, which is the most important thing...later refactor
    	// model and possibly introduce something to allow splitting of
    	// state or passing of partial state through a controller
    	///
    	Map<String, Object> args 
    		= this.test_framework_controller.requestProperties(
    			new String[]{
					TestFrameworkModel.TEST_SIMULATION_FRAMEWORK,
					TestFrameworkModel.TEST_MODULE_MODEL,
					TestFrameworkModel.TEST_HOST_MODEL,
    			}
    		);
    	
    	SimulationFramework simulation_framework
    		= (SimulationFramework) args.get(
    			TestFrameworkModel.TEST_SIMULATION_FRAMEWORK
    		);
    	ModuleModel module_model
    		= (ModuleModel) args.get(
    			TestFrameworkModel.TEST_MODULE_MODEL
    		);
    	HostModel host_model
    		= (HostModel) args.get(
    			TestFrameworkModel.TEST_HOST_MODEL
    		);
    	
    	this.mSimUnitCustomization
    		= new SimulationUnitCustomizationNew(
    			simulation_framework,
    			 new SimulationUnit(
                	module_model.getName(), 
                	(simulation_framework.getTemplate().getAlgorithmName() != null 
                	? simulation_framework.getTemplate().getAlgorithmName()
                	: "Template" ), 
                	new DistributionModel( module_model, host_model )
                )
    		);
    	
    	System.err.println("Created dialog window.");
    	
    	this.mSimUnitCustomization.create();
    	if( this.mSimUnitCustomization.open() == Window.OK ){
    		System.err.println("Returned ok");
    	} 
    }
    
    public void 
    simTableMouseClicked
    ( int id ) 
    {	
        SimulationUnit unit
        	= (SimulationUnit) this.test_framework_controller.index(
        		Constants.SIMULATION_UNITS,
        		new Integer(id)
        	);
        
        assert unit != null : "There is a bug in the program if we received a null unit";
        
        Map<String, Object> map 
        	= this.test_framework_controller.requestProperties(
        		new String[]{
        			TestFrameworkModel.TEST_SIMULATION_FRAMEWORK,
        		}
        	);
        this.mSimUnitCustomization 
        	= new SimulationUnitCustomizationNew(
        		(SimulationFramework) map.get(
        			TestFrameworkModel.TEST_SIMULATION_FRAMEWORK
        		), 
        		unit
            );
        
        System.err.println("Created dialog window.");
    	
    	this.mSimUnitCustomization.create();
    	if( this.mSimUnitCustomization.open() == Window.OK ) {
    		System.err.println("Returned ok");
    	} 	
    }
    
    /////////////////////////////////////////////////////////////////////////////////////////
    
    private class
	TestPageResizedAdapter
	extends ControlAdapter
	{
		Composite 	parent;
		Table		table;
		String[] 	column_names;
		Section 	table_composite;
		Section 	control_composite;
		
		// Struct-style adapter class used to ensure
		// sequencing: the object is not created until all
		// of the fields are initialized to non-null values;
		// Please Read: the arguments to this object must be final, or
		// the control resized handler will not work correctly down the line
		TestPageResizedAdapter
		( Composite parent, Table table, String[] column_names, Section table_composite, Section control_composite)
		{
			if(parent == null){
				throw new NullPointerException();
			}
			
			if(table == null){
				throw new NullPointerException();
			}
			
			if(column_names == null){
				throw new NullPointerException();
			}
			
			if( table_composite == null){
				throw new NullPointerException();
			}
			
			if( control_composite == null){
				throw new NullPointerException();
			}
			
			this.parent = parent;
			this.table = table;
			this.column_names = column_names;
			this.table_composite = table_composite;
			this.control_composite = control_composite;
		}
		
		
		public void controlResized
		( ControlEvent e)
		{
			// will need to ask about the correct way to do this
			// and why 6 works
			
			// ask about how one detects when the scroll bar is visible
			// or not
			int original_width 
				= this.parent.getClientArea().width 
				- 6*this.table.getBorderWidth();
			if(original_width < 750){
				int reduced_width 
					= original_width - this.table.getVerticalBar().getSize().x;
				
				for(int i = 0; i < column_names.length; ++i){
					this.table.getColumn(i).setWidth( 
						reduced_width / column_names.length
					); 
				}
					
				this.table.setSize( reduced_width, ModelTestPage.this.table.getSize().y );
				this.table_composite.setSize(
					original_width,
					this.table_composite.getSize().y
				);
				
				this.control_composite.setSize(
					original_width,
					this.control_composite.getSize().y
				);
				ModelTestPage.this.setSize(
					original_width,
					ModelTestPage.this.getSize().y
				);
			}
		}
	}
}
