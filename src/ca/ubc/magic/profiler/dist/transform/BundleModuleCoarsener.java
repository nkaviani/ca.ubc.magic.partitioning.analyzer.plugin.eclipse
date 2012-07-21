/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.ubc.magic.profiler.dist.transform;

import ca.ubc.magic.profiler.dist.model.interaction.InteractionData;
import ca.ubc.magic.profiler.dist.model.Module;
import ca.ubc.magic.profiler.dist.model.ModuleModel;
import ca.ubc.magic.profiler.dist.model.ModulePair;
import ca.ubc.magic.profiler.dist.model.granularity.CodeEntity;
import ca.ubc.magic.profiler.dist.model.granularity.CodeUnitType;
import ca.ubc.magic.profiler.dist.model.granularity.EntityConstraintModel;
import ca.ubc.magic.profiler.dist.transform.model.NodeObj;
import ca.ubc.magic.profiler.parser.JipFrame;
import ca.ubc.magic.profiler.parser.JipRun;
import java.util.Set;

/**
 *
 * @author nima
 */
public class BundleModuleCoarsener implements IModuleCoarsener {

    protected ModuleModel mModuleModel = new ModuleModel();
    protected Set<CodeEntity> mIgnoreSet = null;
    
    protected EntityConstraintModel mConstraintModel;
    
    public BundleModuleCoarsener(final EntityConstraintModel constraintModel){
        mConstraintModel = constraintModel;
    }
    
    public ModuleModel getModuleModelFromParser(JipRun jipRun) {
    	
    	// TODO tell Nima about this problem
    	// mIgnoreSet = mConstraintModel.getIgnoreSet();
        
        mModuleModel.setName("Profile " + jipRun.getDate());
        for(Long threadId: jipRun.threads()) {
                for (JipFrame f: jipRun.interactions(threadId)) {
                    visitFrameForModuling(f);
                }
           }
        return mModuleModel;
    }        
    
    public void visitFrameForModuling(JipFrame frame){
        
        Module pModule = getFrameModule(frame);
        
        // if the module is not ignored by the system.
        if (pModule != null){
	        pModule.addExecutionCost(new Double(frame.getNetTime()));
	        pModule.addExecutionCount(frame.getCount());
        }
        
        // add info for this frame's children
        for (JipFrame childFrame: frame.getChildren()) {            
            String cmName = getFrameModuleName(childFrame);
            if (pModule == null || !pModule.getName().equals(cmName))
            {   
                Module cModule = getFrameModule(childFrame);
                
                // cModule or pModule might be null if they are
                // said to be ignored by the constrains model fed
                // to the model.
                if (pModule != null && cModule != null){
                	
                	// create the module pair representing the edge
                	ModulePair mp = new ModulePair(pModule, cModule);
                	
                	// add edge to the module model
	                addDataExchange(mp, getFrameModuleDataFromParent(childFrame),
	                        getFrameModuleDataToParent(childFrame),                      
	                        childFrame.getCountFromParent(),
	                        childFrame.getCountToParent());
	                
                }
            }
            // recursive moduling on the ancestors of the childFrame
            visitFrameForModuling(childFrame);
        }        
    }

    public String getFrameModuleName(JipFrame frame) {
        if (mConstraintModel != null)
            return mConstraintModel.getFrameName(frame);
        return frame.getMethod().getBundleName();
    }
    
    public CodeUnitType getFrameModuleType(JipFrame frame) {
        if (mConstraintModel != null)
           return mConstraintModel.getFrameType(frame);
        return CodeUnitType.DEFAULT;
    }
    
    protected Long getFrameModuleDataToParent(JipFrame frame){
        return frame.getDataToParent();
    }
    
    protected Long getFrameModuleDataFromParent(JipFrame frame){
        return frame.getDataFromParent();
    }
    
    protected void addDataExchange(ModulePair mp, Long fromPData, Long toPData, 
            Long fromCount, Long toCount){
        InteractionData dataExchange = mModuleModel.getModuleExchangeMap().get(mp);
        if (dataExchange == null){
            mModuleModel.getModuleExchangeMap().put(mp, new 
                    InteractionData(fromPData, toPData, fromCount, toCount));
        }else {
            dataExchange.addInteraction(fromPData, toPData, fromCount, toCount);
        }
    }
    
    private Module getFrameModule(JipFrame frame){
        String mName = getFrameModuleName(frame);
        CodeUnitType type  = getFrameModuleType(frame);
        
        // if the module needs to be ignored as specified in the constraint model.
        // it returns null as the module for the frame.
        if (shouldIgnore(frame))
        	return null;
        
        if (mModuleModel.getModuleMap().get(mName) == null){
            mModuleModel.getModuleMap().put(mName, new Module(mName, type));
        }
        return mModuleModel.getModuleMap().get(mName);
    }
    
    public ModuleModel getModuleModel(){
       return mModuleModel;
    }
    
    /**
     * determines whether or not the frame should be ignored when building the module model. 
     * 
     * @param frame
     * @return
     */
    private boolean shouldIgnore(JipFrame frame){
    	//TODO nimak - hacked code to deal with jdbc wrapper and execute query. it should stay in the model.
    	if (getFrameModuleName(frame).contains("jdbcwrapper") && getFrameModuleName(frame).contains("executeQuery"))
 		   return false;
    	
        if (mIgnoreSet != null)
               for(CodeEntity entity : mIgnoreSet)
                   if (entity.getEntityPattern().matches(getFrameModuleName(frame), null, null))
                       return true;
        return false;
   }
}
