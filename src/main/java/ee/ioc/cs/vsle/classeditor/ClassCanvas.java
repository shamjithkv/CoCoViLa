package ee.ioc.cs.vsle.classeditor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import ee.ioc.cs.vsle.editor.CodeViewer;
import ee.ioc.cs.vsle.editor.ProgramRunnerEvent;
import ee.ioc.cs.vsle.editor.RuntimeProperties;
import ee.ioc.cs.vsle.editor.State;
import ee.ioc.cs.vsle.event.EventSystem;
import ee.ioc.cs.vsle.graphics.BoundingBox;
import ee.ioc.cs.vsle.graphics.Shape;
import ee.ioc.cs.vsle.graphics.Text;
import ee.ioc.cs.vsle.util.db;
import ee.ioc.cs.vsle.vclass.Canvas;
import ee.ioc.cs.vsle.vclass.GObj;
import ee.ioc.cs.vsle.vclass.Port;
import ee.ioc.cs.vsle.vclass.VPackage;

public class ClassCanvas extends Canvas{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1290323197462938186L;
	public MouseOps mListener; 
    private boolean drawOpenPorts = true;    
    public IconPalette iconPalette;
	public BoundingBox boundingBox;
    

    public ClassCanvas( VPackage _package, String workingDir ) {    	
    	 super(workingDir);
    	 vPackage = _package;
         m_canvasTitle = vPackage.getName();
         initialize();
    }
	
    
    @Override
    protected void initialize() {
    	super.initialize();
        mListener = new MouseOps( this );     
       //DrawingArea 
     //  drawingArea =  getDrawingArea();
       drawingArea.addMouseListener( mListener );
     //   super.drawingArea.add
        drawingArea.addMouseMotionListener( mListener );
    }
    
    public void updateBoundingBox(){
    	GObj bbObj = null;
    	for (GObj obj : getObjectList()) {
            for (Shape shape : obj.getShapes()) {
				if (shape instanceof BoundingBox) {
					bbObj = obj;
				} 								 
			}           
        }    	
    	if(bbObj != null){
    		 for (Shape shape : bbObj.getShapes()) {
 				if (shape instanceof Text) {
 					((Text) shape).setText(getTextForBoundingBox());
 				} 								 
 			}  
    		repaint();
    	}
    }
    
    public String getTextForBoundingBox(){
    	String text = "ClassNameNotDefined";
    	if (ClassEditor.classObject != null && ClassEditor.classObject.getClassName() != null &&  ClassEditor.classObject.getClassName() != ""){
    		text =  ClassEditor.classObject.getClassName();
    	}
    	return text;
    }
    
    public Shape drawTextForBoundingBox(int x, int y){    	
    	return drawTextForBoundingBox(x, y, getTextForBoundingBox());
    }
    
    
    public void clearObjects() {
    	super.clearObjects();
    	if(iconPalette != null && iconPalette.boundingbox.isSelected()){
    		iconPalette.selection.setSelected(true);
    		iconPalette.boundingbox.setSelected(false);
    	}
        drawingArea.repaint();
    }
    
    public Shape drawTextForBoundingBox(int x, int y, String text){    
    			 
    	/* Magic number to position text on BB */
    			 if(x < 100){
    				 x = x/2;
    			 } else {
    				 x = x - 100;
    			 }    			 
    	    	 Shape sText = new Text( x, y + 15,
    			 new Font("Arial", Font.BOLD, 13), Color.black, text);
    	return sText;
    }
    
    @Override
    public DrawingArea getDrawingArea() {
    	drawingArea = new DrawingArea();
    	drawingArea.init();
        return  (DrawingArea) drawingArea;
    }

    public class DrawingArea extends Canvas.DrawingArea {
       
		private static final long serialVersionUID = 7866172800421330602L;

		@Override        
        protected void paintComponent( Graphics g ) {
			super.paintComponent(g);
		        Graphics2D g2 = (Graphics2D) g;
		        
		        //     hide or show BoundingBox
		        if(iconPalette != null){
		        	iconPalette.boundingbox.setEnabled( !isBBPresent() );
		        }
		        
		        // coordinates
		          int rectX = Math.min( mListener.startX, mouseX );
		          int rectY = Math.min( mListener.startY, mouseY );
		          int width = Math.abs( mouseX - mListener.startX );
		          int height = Math.abs( mouseY - mListener.startY );
		        
		        // Draw Shapes
		                
		         if ( mListener.state.equals( State.drawArc1 ) ) {
		             g.drawRect( mListener.startX, mListener.startY, mListener.arcWidth, mListener.arcHeight );
		             g.drawLine( mListener.startX + mListener.arcWidth / 2, mListener.startY + mListener.arcHeight / 2, mouseX,
		                     mouseY );
		         } else if ( mListener.state.equals( State.drawArc2 ) ) {
		             if ( mListener.fill ) {
		                 g2.fillArc( mListener.startX, mListener.startY, mListener.arcWidth, mListener.arcHeight,
		                         mListener.arcStartAngle, mListener.arcAngle );

		             } else {
		                 g2.drawArc( mListener.startX, mListener.startY, mListener.arcWidth, mListener.arcHeight,
		                         mListener.arcStartAngle, mListener.arcAngle );
		             }

			
		}	
		         if ( !mListener.mouseState.equals( "released" ) ) {

		             if ( mListener.state.equals( State.dragBox ) 
		                     || mListener.state.equals( State.boundingbox )) {
		                    g2.setColor( Color.gray );
		                    g2.drawRect( rectX, rectY, width, height );
		                } else {
		                    
		                    int red = mListener.color.getRed();
		                    int green = mListener.color.getGreen();
		                    int blue = mListener.color.getBlue();

		                    int alpha = mListener.getTransparency();
		                    g2.setColor( new Color( red, green, blue, alpha ) );

		                    if ( mListener.lineType > 0 ) {
		                        g2.setStroke( new BasicStroke( mListener.strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
		                                50, new float[] { mListener.lineType, mListener.lineType }, 0 ) );
		                    } else {
		                        g2.setStroke( new BasicStroke( mListener.strokeWidth ) );
		                    }
		            
		                    if ( mListener.state.equals( State.drawRect ) ) {
		                        g2.setColor( mListener.color );
		                        g2.drawRect( rectX, rectY, width, height );
		                    } else if ( mListener.state.equals( State.drawFilledRect ) ) {
		                        g2.setColor( mListener.color );
		                        g2.fillRect( rectX, rectY, width, height );    
		                 } else if ( mListener.state.equals( State.drawLine ) ) {
		                     g2.drawLine( mListener.startX, mListener.startY, mouseX, mouseY );                        
		                    } else if ( mListener.state.equals( State.drawOval ) ) {
		                        g2.setColor( mListener.color );
		                        g2.drawOval( rectX, rectY, width, height );
		                    } else if ( mListener.state.equals( State.drawFilledOval ) ) {
		                        g2.setColor( mListener.color );
		                        g2.fillOval( rectX, rectY, width, height );
		                 } else if ( mListener.state.equals( State.drawArc ) ) {
		                     g.drawRect( rectX, rectY, width, height );
		                 } else if ( mListener.state.equals( State.drawFilledArc ) ) {
		                     g.drawRect( rectX, rectY, width, height );
		        }        

		                }
		         }
		}
    }
    public void setDrawOpenPorts(boolean drawOpenPorts) {
		
		this.drawOpenPorts = drawOpenPorts;		
		for (GObj obj : scheme.getObjectList()) {
		    obj.setDrawOpenPorts( drawOpenPorts );
        }
		drawingArea.repaint();
	}
    
    public boolean isDrawOpenPorts() {
		return drawOpenPorts;
	}
    
    public BoundingBox getBoundingBox() {
		return boundingBox;
	}
    
    public boolean isBBPresent() {
        boolean isBbPresent = false;
        for (GObj obj : getObjectList()) {
            for (Shape shape : obj.getShapes()) {
				if (shape instanceof BoundingBox) {
					isBbPresent = true;
					boundingBox = new BoundingBox(obj.getX(),obj.getY(), shape.getWidth(), shape.getHeight());					
					break;
				}
			}
        }
        return isBbPresent;
    }
    
    public boolean isBBTop() {
        boolean isBbTop = false;
        for (GObj obj : getObjectList()) {
            for (Shape shape : obj.getShapes()) {
				if (shape instanceof BoundingBox) {
				   if(getObjectList().indexOf(obj) == getObjectList().size() - 1)
					   isBbTop = true;
					}			
					break;
				}
			}
         return isBbTop;
    }
    
    @Override
    public void openClassCodeViewer( String className ) {
    	if (className == null){
    		  JOptionPane.showMessageDialog(this, 
                      "View Code failed: no class name\n" +
                      "\nYou may need to revise the application settings.",
                      "Error Running External Editor",
                      JOptionPane.ERROR_MESSAGE);
    		return;
    	}
        String editor = RuntimeProperties.getDefaultEditor();        
        if (editor == null) {
            CodeViewer cv = new CodeViewer(className, RuntimeProperties.getLastPath()); /* java file is in same dir as Package*/
            cv.setLocationRelativeTo( ClassEditor.getInstance() );
            cv.open();
       } else {
       // if(editor != null && className != null) {
            File wd = new File(getWorkDir());
            String editCmd = editor.replace("%f",
                    new File(className + ".java").getPath());

            try {
                Runtime.getRuntime().exec(editCmd, null, wd);
            } catch (IOException ex) {
                if (RuntimeProperties.isLogDebugEnabled()) {
                    db.p(ex);
                }
                JOptionPane.showMessageDialog(this, 
                        "Execution of the command \"" + editCmd
                        + "\" failed:\n" + ex.getMessage() +
                        "\nYou may need to revise the application settings.",
                        "Error Running External Editor",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Sets actionInProgress. Actions that consist of more than one atomic step
     * that cannot be interleaved with other actions should set this property
     * and unset it after completion. For example, consider this scenario:
     * <ol>
     * <li>a new object is created</li>
     * <li>a new connection is connected to the new object</li>
     * <li>before connecting a second object the addition of the object is
     * undone</li>
     * </ol>
     * This is a case when undo-redo should be disabled until either the
     * connection is cancelled or the second end is connected.
     * 
     * @param newValue the actionInProgress value
     */
    public void setActionInProgress( boolean newValue ) {
        if ( newValue != actionInProgress ) {
            actionInProgress = newValue;
            ClassEditor editor = ClassEditor.getInstance();
            editor.deleteAction.setEnabled( !newValue );
            editor.refreshUndoRedo();
        }
    }
    
    @Override
    public void destroy() {
        
        for ( long id : super.m_runners ) {
            ProgramRunnerEvent event = new ProgramRunnerEvent( this, id, ProgramRunnerEvent.DESTROY );

            EventSystem.queueEvent( event );
        }
        
        iconPalette.destroy();
        iconPalette = null;
        
        drawingArea.removeMouseListener( mListener );
        drawingArea.removeMouseMotionListener( mListener );
        drawingArea.removeKeyListener( keyListener );
        drawingArea.removeAll();
        drawingArea.setFocusable( false );
        drawingArea = null;
        areaScrollPane.removeAll();
        areaScrollPane = null;
        removeAll();
        
        mListener.destroy();
        mListener = null;
        
        this.removeKeyListener( keyListener );
        keyListener.destroy();
        keyListener = null;
        
        if( scheme != null ) {
            scheme.destroy();
            scheme = null;
        }
        vPackage = null;
        super.classPainters = null;
        currentPainter = null;
        currentObj = null;
        currentCon = null;
        drawAreaSize = null;
        infoPanel = null;
        posInfo = null;
        backgroundImage = null;
        
        executor.shutdownNow();
        executor = null;
        undoManager = null;
        undoSupport = null;
    }
    
    
    /**
     * Starts adding a new relation object.
     * 
     * @param port the first port of the relation object
     */
    protected void startAddingRelObject( Port port ) {    	
    	super.startAddingRelObject(port);
        setActionInProgress( true );
    }

    protected void cancelAddingObject() {
    	super.cancelAddingObject();
        setActionInProgress( false );
    }

}