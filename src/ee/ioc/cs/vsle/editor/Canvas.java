package ee.ioc.cs.vsle.editor;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import javax.xml.transform.*;
import javax.xml.transform.sax.*;
import javax.xml.transform.stream.*;

import org.xml.sax.helpers.*;

import ee.ioc.cs.vsle.event.*;
import ee.ioc.cs.vsle.util.*;
import ee.ioc.cs.vsle.vclass.*;
import ee.ioc.cs.vsle.vclass.Point;

/**
 */
public class Canvas extends JPanel implements ISchemeContainer {
    
    private static final long serialVersionUID = 1L;
    int mouseX; // Mouse X coordinate.
    int mouseY; // Mouse Y coordinate.
    private String workDir;
    private VPackage vPackage;
    Palette palette;
    private Scheme scheme;
    private ConnectionList connections;
    private ObjectList objects;
    Map<GObj, ClassPainter> classPainters;
    ClassPainter currentPainter;
    GObj currentObj;
    Connection currentCon;
    public MouseOps mListener;
    public KeyOps keyListener;
    boolean showGrid = false;
    Dimension drawAreaSize = new Dimension( 600, 500 );
    JPanel infoPanel;
    JLabel posInfo;
    DrawingArea drawingArea;
    BufferedImage backgroundImage;
    ExecutorService executor;
    float scale = 1.0f;
    boolean enableClassPainter = true;
    UndoManager undoManager;
    UndoableEditSupport undoSupport;
    private boolean actionInProgress = false;
    private JScrollPane areaScrollPane;
    private boolean drawPorts = true;
    private boolean showObjectNames = false;
    
    private FontChangeEvent.Listener fontListener = new FontChangeEvent.Listener() {

        @Override
        public void fontChanged( FontChangeEvent e ) {
            if( drawingArea != null && ( e.getElement() == RuntimeProperties.Fonts.OBJECTS 
                    || e.getElement() == RuntimeProperties.Fonts.STATIC ) ) {

                drawingArea.repaint();
            }
        }
    };
    
    /*
     * The Edit classes implementing undo-redo could be moved somewhere else but
     * as they need to touch the internal state of the Canvas object then maybe
     * there is no better place right now. Limitations: Should preserve
     * uniqueness of the object names somehow.
     */

    /**
     * Undoable edit for setting scheme super class. This edit is trivial but
     * undo support is needed to keep the state consistent. Otherwise it would
     * be, for example, possible to accidentally add relations to the superclass
     * or perform other actions that would cause problems later.
     */
    private static class SetSuperClassEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private GObj object;
        private boolean value;

        public SetSuperClassEdit( GObj obj, boolean value ) {
            this.object = obj;
            this.value = value;
        }

        @Override
        public String getPresentationName() {
            return value ? "Set Superclass" : "Unset Superclass";
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            object.setSuperClass( value );
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            object.setSuperClass( !value );
        }
    }

    /**
     * Undoable edit for moving objects on the scheme. Multiple subsequent move
     * edits of the same set of objects are merged into one movement. The effect
     * of undoing the merged edit is to move the objects back to the positions
     * they had before the first move edit. The edit keeps track and takes care
     * of strict connections created or breaked during the movements.
     */
    private static class MoveEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private Canvas canvas;
        private int moveX;
        private int moveY;
        private Collection<GObj> selected;
        private Collection<Connection> newConns;
        private Collection<Connection> delConns;

        public MoveEdit( Canvas canvas, int moveX, int moveY, Collection<GObj> selectedObjs, Collection<Connection> created,
                Collection<Connection> deleted ) {

            this.canvas = canvas;
            this.moveX = moveX;
            this.moveY = moveY;
            this.selected = selectedObjs;

            // remember to create copies before modifying
            this.newConns = created;
            this.delConns = deleted;
        }

        @Override
        public boolean addEdit( UndoableEdit anEdit ) {
            if ( anEdit instanceof MoveEdit ) {
                MoveEdit ne = (MoveEdit) anEdit;

                if ( ne.selected.equals( selected ) ) {
                    moveX += ne.moveX;
                    moveY += ne.moveY;

                    if ( newConns == null && ne.newConns != null )
                        newConns = new ArrayList<Connection>( ne.newConns );

                    // Add all removed connections that were not created
                    // in previous move edits to the deleted list. Forget
                    // about connections which were created and deleted.
                    if ( ne.delConns != null ) {
                        for ( Connection con : ne.delConns ) {
                            if ( newConns == null || !newConns.remove( con ) ) {
                                if ( delConns == null )
                                    delConns = new ArrayList<Connection>();

                                delConns.add( con );
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getPresentationName() {
            return "Move";
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();

            moveSelected( moveX, moveY );

            if ( delConns != null )
                canvas.connections.removeAll( delConns );
            if ( newConns != null )
                canvas.connections.addAll( newConns );

            canvas.objects.updateRelObjs();
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();

            moveSelected( -moveX, -moveY );

            if ( newConns != null )
                canvas.connections.removeAll( newConns );
            if ( delConns != null )
                canvas.connections.addAll( delConns );

            canvas.objects.updateRelObjs();
        }

        private void moveSelected( int dx, int dy ) {
            for ( GObj obj : selected ) {
                obj.setX( obj.getX() + dx );
                obj.setY( obj.getY() + dy );

                for ( Connection con : obj.getConnections() ) {
                    if ( selected.contains( con.beginPort.getObject() ) && selected.contains( con.endPort.getObject() ) ) {

                        con.move( dx, dy );
                    }
                }
            }
        }
    }

    /**
     * Undoable edit for deleting a connection.
     */
    private static class DeleteConnectionEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private Canvas canvas;
        private Connection connection;

        public DeleteConnectionEdit( Canvas canvas, Connection connection ) {
            this.canvas = canvas;
            this.connection = connection;
        }

        @Override
        public String getPresentationName() {
            return "Delete relation";
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            canvas.getConnections().remove( connection );
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            canvas.getConnections().add( connection );
        }
    }

    /**
     * Undoable edit for adding a connection.
     */
    private static class AddConnectionEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private Canvas canvas;
        private Connection connection;

        public AddConnectionEdit( Canvas canvas, Connection connection ) {
            this.canvas = canvas;
            this.connection = connection;
        }

        @Override
        public String getPresentationName() {
            return "Add relation";
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            canvas.getConnections().add( connection );
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            canvas.getConnections().remove( connection );
        }
    }

    /**
     * Undoable edit for adding objects.
     */
    private static class AddObjectEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private GObj object;
        private ClassPainter painter;
        private Canvas canvas;
        private Collection<Connection> newConns;

        public AddObjectEdit( Canvas canvas, GObj currentObj, ClassPainter currentPainter, ArrayList<Connection> newConns ) {
            this.canvas = canvas;
            this.object = currentObj;
            this.painter = currentPainter;
            this.newConns = newConns;
        }

        @Override
        public String getPresentationName() {
            return "Insert " + object.className;
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            canvas.objects.add( object );
            canvas.objects.updateRelObjs();
            if ( newConns != null )
                canvas.connections.addAll( newConns );
            if ( painter != null && canvas.classPainters != null )
                canvas.classPainters.put( object, painter );
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            canvas.objects.remove( object );
            if ( newConns != null )
                canvas.connections.removeAll( newConns );
            if ( painter != null && canvas.classPainters != null )
                canvas.classPainters.remove( object );
        }
    }

    /**
     * Undoable edit for removing selected objects from the scheme.
     */
    private static class DeleteEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private Canvas canvas;
        private Collection<GObj> removedObjs;
        private Collection<Connection> removedConns;
        private Map<GObj, ClassPainter> removedPainters;

        public DeleteEdit( Canvas canvas, Collection<GObj> removedObjs, Collection<Connection> removedConns,
                Map<GObj, ClassPainter> removedPainters ) {

            this.canvas = canvas;
            this.removedObjs = removedObjs;
            this.removedConns = removedConns;
            this.removedPainters = removedPainters;
        }

        @Override
        public String getPresentationName() {
            return Menu.DELETE;
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            canvas.objects.removeAll( removedObjs );
            canvas.connections.removeAll( removedConns );
            if ( canvas.classPainters != null && removedPainters != null ) {
                canvas.classPainters.entrySet().removeAll( removedPainters.entrySet() );
            }
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            canvas.objects.addAll( removedObjs );
            canvas.objects.updateRelObjs();
            canvas.connections.addAll( removedConns );
            if ( canvas.classPainters != null && removedPainters != null )
                canvas.classPainters.putAll( removedPainters );
        }
    }

    /**
     * Undoable edit for cloning scheme objects.
     */
    private static class CloneEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private Canvas canvas;
        private Collection<GObj> newObjects;
        private Collection<Connection> newConnections;
        private Map<GObj, ClassPainter> newPainters;

        public CloneEdit( Canvas canvas, Collection<GObj> newObjects, Collection<Connection> newConnections,
                Map<GObj, ClassPainter> newPainters ) {

            this.canvas = canvas;
            this.newObjects = newObjects;
            this.newConnections = newConnections;
            this.newPainters = newPainters;
        }

        @Override
        public String getPresentationName() {
            return Menu.CLONE;
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            canvas.objects.addAll( newObjects );
            canvas.connections.addAll( newConnections );
            if ( canvas.classPainters != null && newPainters != null )
                canvas.classPainters.putAll( newPainters );
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            canvas.objects.removeAll( newObjects );
            canvas.connections.removeAll( newConnections );
            if ( canvas.classPainters != null && newPainters != null ) {
                canvas.classPainters.entrySet().removeAll( newPainters.entrySet() );
            }
        }
    }

    /**
     * Undoable edit for removing all objects from the scheme.
     */
    private static class ClearAllEdit extends AbstractUndoableEdit {

        private static final long serialVersionUID = 1L;

        private Canvas canvas;

        private Collection<Connection> connCopy;
        private Collection<GObj> objCopy;
        private Map<GObj, ClassPainter> painterCopy;

        public ClearAllEdit( Canvas canvas ) {
            this.canvas = canvas;

            objCopy = new ArrayList<GObj>( canvas.objects );
            connCopy = new ArrayList<Connection>( canvas.connections );
            if ( canvas.classPainters != null )
                painterCopy = new HashMap<GObj, ClassPainter>( canvas.classPainters );
        }

        @Override
        public String getPresentationName() {
            return Menu.CLEAR_ALL;
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            canvas.objects.clear();
            canvas.connections.clear();
            if ( canvas.classPainters != null )
                canvas.classPainters.clear();
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            canvas.objects.addAll( objCopy );
            canvas.connections.addAll( connCopy );
            if ( canvas.classPainters != null )
                canvas.classPainters.putAll( painterCopy );
        }
    }

    public Canvas( VPackage _package, String workingDir ) {
        super();
        
        setWorkDir( workingDir );
        vPackage = _package;
        initialize();
        palette = new Palette( vPackage, this );
        m_canvasTitle = vPackage.getName();
        FontChangeEvent.addFontChangeListener( fontListener );
        validate();
    }

    private String m_canvasTitle;

    public void setTitle( String title ) {
        m_canvasTitle = title;
    }

    public String getTitle() {
        return m_canvasTitle;
    }

    /**
     * Returns the filename part of package's last scheme path.
     * 
     * @return file name without path and suffix which may be empty string, or
     *         null if the scheme is not saved to or loaded from a file.
     */
    public String getSchemeTitle() {
        String lastFile = vPackage.getLastScheme();
        String title = null;
        if ( lastFile != null && lastFile.length() > 0 ) {
            int is = lastFile.lastIndexOf( File.separatorChar );
            int ip = lastFile.lastIndexOf( '.' );

            is = is > 0 ? is + 1 : 0;
            ip = ip > 0 ? ip : lastFile.length() - 1;

            if ( is <= ip )
                title = lastFile.substring( is, ip );
        }
        return title;
    }

    void initialize() {
        scheme = new Scheme( vPackage );
        setObjects( scheme.getObjects() );
        connections = scheme.getConnections();
        mListener = new MouseOps( this );
        keyListener = new KeyOps( this );
        drawingArea = new DrawingArea();
        drawingArea.setOpaque( true );
        drawingArea.setBackground( Color.white );
        setGridVisible( RuntimeProperties.isShowGrid() );
        drawingArea.setFocusable( true );
        infoPanel = new JPanel( new GridLayout( 1, 2 ) );
        posInfo = new JLabel();
        drawingArea.addMouseListener( mListener );
        drawingArea.addMouseMotionListener( mListener );
        drawingArea.setPreferredSize( drawAreaSize );

        // Initializes key listeners, for keyboard shortcuts.
        drawingArea.addKeyListener( keyListener );

        areaScrollPane = new JScrollPane( drawingArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED );
        setLayout( new BorderLayout() );
        add( areaScrollPane, BorderLayout.CENTER );

        infoPanel.add( posInfo );
        posInfo.setText( "-" );

        add( infoPanel, BorderLayout.SOUTH );

        executor = Executors.newSingleThreadExecutor();

        if ( vPackage.hasPainters() ) {
            classPainters = new HashMap<GObj, ClassPainter>();
            if ( !createPainterPrototypes() ) {
                JOptionPane.showMessageDialog( this, "One or more errors occured. See the error log for details.", "Error",
                        JOptionPane.ERROR_MESSAGE );
            }
        }

        undoManager = new UndoManager();
        undoSupport = new UndoableEditSupport();
        undoSupport.addUndoableEditListener( undoManager );
        undoSupport.addUndoableEditListener( new UndoableEditListener() {
            public void undoableEditHappened( UndoableEditEvent e ) {
                Editor.getInstance().refreshUndoRedo();
            }
        } );
        setScale( RuntimeProperties.getZoomFactor() );
    }

    private boolean createPainterPrototypes() {
        boolean success = true;
        /* TODO Will be replaced by more general daemon stuff
        PackageClassLoader pcl = null;

        for ( PackageClass pclass : vPackage.classes ) {
            if ( pclass.painterName == null )
                continue;

            try {
                if (pcl == null) {
                    pcl = vPackage.getPackageClassLoader();
                }
                Class<?> painterClass = pcl.loadClass(pclass.painterName);
                pclass.setPainterPrototype((ClassPainter) painterClass.newInstance());
            } catch ( CompileException e ) {
                success = false;
                db.p( e ); // print compiler generated message
            } catch ( Exception e ) {
                success = false;
                db.p( e );
            } finally {
                if (pcl != null && pcl.hasErrors()) {
                    pcl.clearProblems();
                }
            }
        }
        */
        return success;
    }

    public VPackage getPackage() {
        return vPackage;
    }

    /**
     * Method for grouping objects.
     */
    public void groupObjects() {
        throw new UnsupportedOperationException();
        /*
         * This function is broken and was hidden in the GUI. If this is
         * something useful then it should be specified and reimplemented or
         * something.
         */
        /*
         * ArrayList<GObj> selected = objects.getSelected(); if
         * (selected.size() > 1) { GObj obj; for (int i = 0; i <
         * selected.size(); i++) { obj = selected.get(i);
         * obj.setSelected(false); } GObjGroup og = new GObjGroup(selected);
         * og.setAsGroup(true); objects.removeAll(selected); objects.add(og);
         * drawingArea.repaint(); }
         */
    } // groupObjects

    /**
     * Moves selected objects on the scheme.
     * 
     * @param moveX X coordinate change
     * @param moveY Y coordinate change
     */
    public void moveObjects( int moveX, int moveY ) {
        ArrayList<GObj> selectedObjs = objects.getSelected();
        ArrayList<Connection> created = null;
        ArrayList<Connection> deleted = null;

        for ( int i = 0; i < selectedObjs.size(); i++ ) {
            GObj obj = selectedObjs.get( i );
            if ( ! ( obj instanceof RelObj ) )
                obj.setPosition( obj.getX() + moveX, obj.getY() + moveY );

            if ( obj.isStrict() ) {
                // remove broken strict connections
                ArrayList<Connection> rc = getBrokenStrictConnections( obj );

                if ( rc != null ) {
                    if ( deleted == null )
                        deleted = new ArrayList<Connection>();

                    deleted.addAll( rc );
                    connections.removeAll( rc );
                }

                // create new strict connections
                ArrayList<Connection> nc = getNewStrictConnections( obj );

                if ( nc != null ) {
                    if ( created == null )
                        created = new ArrayList<Connection>();

                    created.addAll( nc );
                    connections.addAll( nc );
                }
            }
        }

        // if both endpoints of a connection are moved
        // then move the breakpoints also
        for ( Connection con : connections ) {
            if ( selectedObjs.contains( con.beginPort.getObject() ) && selectedObjs.contains( con.endPort.getObject() ) ) {
                con.move( moveX, moveY );
            }
        }

        MoveEdit edit = new MoveEdit( this, moveX, moveY, selectedObjs, created, deleted );
        undoSupport.postEdit( edit );

        objects.updateRelObjs();
        drawingArea.repaint();
    } // moveObject

    /**
     * Method for ungrouping objects.
     */
    public void ungroupObjects() {
        throw new UnsupportedOperationException();
        /*
         * See groupObjects()
         */
        /*
         * GObj obj; for (int i = 0; i < objects.getSelected().size(); i++) {
         * obj = objects.getSelected().get(i); if (obj.isGroup()) {
         * objects.addAll(((GObjGroup) obj).objects); objects.remove(obj); obj =
         * null; currentObj = null; } } drawingArea.repaint();
         */
    }

    public void addCurrentObject() {
        addCurrentObject( null );
    }

    /**
     * Adds the current object to the scheme.
     */
    public void addCurrentObject( Port endPort ) {
        if ( currentObj == null )
            throw new IllegalStateException( "Current object is null" );

        objects.add( currentObj );
        if ( classPainters != null && currentPainter != null )
            classPainters.put( currentObj, currentPainter );

        ArrayList<Connection> newConns = null;

        if ( currentObj instanceof RelObj ) {
            RelObj obj = (RelObj) currentObj;

            obj.endPort = endPort;

            obj.startPort.setSelected( false );
            obj.endPort.setSelected( false );

            ArrayList<Port> ports = currentObj.getPorts();

            newConns = new ArrayList<Connection>( 2 );
            newConns.add( new Connection( obj.startPort, ports.get( 0 ) ) );
            newConns.add( new Connection( ports.get( 1 ), endPort ) );
            connections.addAll( newConns );

            objects.updateRelObjs();
        } else if ( currentObj.isStrict() ) {
            newConns = getNewStrictConnections( currentObj );
            if ( newConns != null )
                connections.addAll( newConns );
        }

        undoSupport.postEdit( new AddObjectEdit( this, currentObj, currentPainter, newConns ) );

        currentObj = null;
        currentPainter = null;
        setActionInProgress( false );
    }

    /**
     * Examines the strict ports of the object and creates new connections where
     * necessary. Connections are created when the center point of one strict
     * port is inside another strict port and there is no direct connection
     * between these ports yet an the types of the ports are compatible.
     * 
     * @param object a strict object
     * @return a list of new connections, {@code null} if no new connections
     *         will be created
     */
    private ArrayList<Connection> getNewStrictConnections( GObj object ) {
        ArrayList<Connection> conns = null;
        for ( Port port : object.getPorts() ) {
            if ( port.isStrict() ) {
                port.setSelected( false );

                Point p1 = object.toCanvasSpace( port.getRealCenterX(), port.getRealCenterY() );
                
                Port port2 = objects.getPort( p1.x, p1.y, object );

                if ( port2 != null && port2.isStrict() && port.canBeConnectedTo( port2 ) && !port.isConnectedTo( port2 ) ) {

                    // do not create more than one connection to a strict port
                    boolean ignore = false;

                    for ( Connection c : object.getConnections() ) {
                        if ( c.isStrict() && ( c.beginPort == port2 || c.endPort == port2 ) ) {
                            ignore = true;
                            break;
                        }

                    }

                    if ( !ignore && conns != null ) {
                        for ( Connection c : conns ) {
                            if ( c.beginPort == port2 || c.endPort == port2 ) {
                                ignore = true;
                                break;
                            }
                        }
                    }

                    if ( !ignore ) {
                        if ( conns == null )
                            conns = new ArrayList<Connection>();

                        conns.add( new Connection( port, port2, true ) );
                    }
                }
            }
        }
        return conns;
    }

    /**
     * Returns the list of strict connections that should be removed because the
     * ports of the specified object are not close enough to the corresponding
     * strictly connected port.
     * 
     * @param object the owner of the strict ports to be examined
     * @return the list of strict connections that should be removed
     */
    private ArrayList<Connection> getBrokenStrictConnections( GObj object ) {
        ArrayList<Connection> removed = null;

        for ( Port port : object.getPorts() ) {
            for ( Connection con : port.getConnections() ) {
                if ( con.isStrict() && ! ( con.beginPort.contains( con.endPort ) || con.endPort.contains( con.beginPort ) ) ) {

                    if ( removed == null )
                        removed = new ArrayList<Connection>();

                    removed.add( con );
                }
            }
        }

        return removed;
    }

    /**
     * Method for deleting selected objects.
     */
    public void deleteObjects() {
        Collection<GObj> removableObjs = new ArrayList<GObj>();
        Collection<Connection> removableConns = new ArrayList<Connection>();
        Map<GObj, ClassPainter> rmPainters = new HashMap<GObj, ClassPainter>();
        Connection con;

        // remove selected objects and related connections and
        // accumulate for undo
        GObj obj;
        for ( int i = 0; i < objects.size(); i++ ) {
            obj = objects.get( i );
            if ( obj.isSelected() ) {
                Collection<Connection> cs = obj.getConnections();
                removableConns.addAll( cs );
                connections.removeAll( cs );
                removableObjs.add( obj );
                deleteClassPainters( obj, rmPainters );
            }
        }
        objects.removeAll( removableObjs );

        // remove selected connections and accumulate them for undo
        for ( int i = 0; i < connections.size(); ) {
            con = connections.get( i );
            if ( con.isSelected() ) {
                removableConns.add( con );
                connections.remove( con );
            } else
                i++;
        }

        // remove dangling relation objects
        for ( RelObj ro : objects.getExcessRels() ) {
            Collection<Connection> cs = ro.getConnections();
            // do not undelete a connection more than once
            for ( Connection c : cs ) {
                if ( connections.contains( c ) )
                    removableConns.add( c );
            }
            connections.removeAll( cs );
            removableObjs.add( ro );
            deleteClassPainters( ro, rmPainters );
            objects.remove( ro );
        }

        if ( removableObjs.size() > 0 || removableConns.size() > 0 ) {
            DeleteEdit edit = new DeleteEdit( this, removableObjs, removableConns, rmPainters );

            undoSupport.postEdit( edit );
        }

        drawingArea.repaint();
    }

    /**
     * Removes all classpainters associated to the object {@code obj}. When the
     * {@code obj} is an object group then classpainters are removed
     * recursively. All the removed classpainters are stored in the collection
     * {@code rmPainters} if it is not {@code null}.
     * 
     * @param obj The object whose classpainters are to be removed.
     * @param rmPainters The collection where removed classpainters are stored
     *                if non-{@code null}.
     */
    private void deleteClassPainters( GObj obj, Map<GObj, ClassPainter> rmPainters ) {

        if ( classPainters == null )
            return;

        if ( obj.isGroup() ) {
            for ( GObj component : obj.getComponents() )
                deleteClassPainters( component, rmPainters );
        } else {
            ClassPainter painter = classPainters.remove( obj );
            if ( painter != null && rmPainters != null )
                rmPainters.put( obj, painter );
        }
    }

    private void initClassPainters() {
        if ( !vPackage.hasPainters() ) {
            classPainters = null;
            return;
        }

        if ( classPainters == null )
            classPainters = new HashMap<GObj, ClassPainter>();

        for ( GObj obj : objects ) {
            PackageClass pc = vPackage.getClass( obj.getClassName() );
            if ( pc != null ) {
                ClassPainter painter = pc.getPainterFor( scheme, obj );
                if ( painter != null )
                    classPainters.put( obj, painter );
            }
        }
    }

    public void selectAllObjects() {
        GObj obj;
        for ( int i = 0; i < objects.size(); i++ ) {
            obj = objects.get( i );
            obj.setSelected( true );
        }
        drawingArea.repaint();
    } // selectAllObjects

    /**
     * Remove all objects, connections and classpainters.
     */
    public void clearObjects() {
        ClearAllEdit edit = new ClearAllEdit( this );

        mListener.setState( State.selection );
        objects.clear();
        connections.clear();
        if ( classPainters != null )
            classPainters.clear();

        undoSupport.postEdit( edit );
        drawingArea.repaint();
    }

    /**
     * Clones selected objects.
     */
    public void cloneObject() {
        ArrayList<GObj> newObjects = new ArrayList<GObj>();
        Map<GObj, ClassPainter> newPainters = null;

        // clone every selected object
        for ( GObj obj : objects.getSelected() ) {
            GObj newObj = obj.clone();
            newObj.setPosition( newObj.x + 20, newObj.y + 20 );
            newObjects.addAll( newObj.getComponents() );

            // create new and fresh class painter for cloned object
            if ( vPackage.hasPainters() ) {
                PackageClass pc = vPackage.getClass( obj.getClassName() );
                ClassPainter painter = pc.getPainterFor( scheme, newObj );
                if ( painter != null ) {
                    if ( newPainters == null )
                        newPainters = new HashMap<GObj, ClassPainter>();
                    newPainters.put( newObj, painter );
                }
            }

            obj.setSelected( false );
        }

        for ( GObj obj : newObjects ) {
            if ( obj instanceof RelObj ) {
                RelObj robj = (RelObj) obj;
                for ( GObj obj2 : newObjects ) {
                    if ( robj.startPort.getObject().getName().equals( obj2.getName() ) ) {
                        robj.startPort.setObject( obj2 );
                    }
                    if ( robj.endPort.getObject().getName().equals( obj2.getName() ) ) {
                        robj.endPort.setObject( obj2 );
                    }
                }
            }
        }

        // now the hard part - we have to clone all the connections
        ArrayList<Connection> newConnections = new ArrayList<Connection>();
        for ( Connection con : connections ) {
            GObj beginObj = null;
            GObj endObj = null;

            for ( GObj obj : newObjects ) {
                if ( obj.getName().equals( con.beginPort.getObject().getName() ) )
                    beginObj = obj;
                if ( obj.getName().equals( con.endPort.getObject().getName() ) )
                    endObj = obj;

                if ( beginObj != null && endObj != null ) {
                    Connection newCon = new Connection( beginObj.getPorts().get( con.beginPort.getNumber() ), endObj.getPorts()
                            .get( con.endPort.getNumber() ), con.isStrict() );

                    for ( Point p : con.getBreakPoints() )
                        newCon.addBreakPoint( new Point( p.x + 20, p.y + 20 ) );

                    newConnections.add( newCon );
                    break;
                }
            }
        }

        for ( GObj obj : newObjects )
            obj.setName( genObjectName( vPackage.getClass( obj.className ), obj ) );

        objects.addAll( newObjects );

        // New connections have to be added after the new objects have been
        // committed or new ports will not get connected properly.
        connections.addAll( newConnections );

        if ( classPainters != null && newPainters != null )
            classPainters.putAll( newPainters );

        undoSupport.postEdit( new CloneEdit( this, newObjects, newConnections, newPainters ) );

        drawingArea.repaint();
    }

    /**
     * Hilight ports of the object.
     */
    public void hilightPorts() {
        for (GObj obj : objects.getSelected()) {
            for (Port p : obj.getPorts()) {
                p.setHilighted( !p.isHilighted() );
            }
        }
        drawingArea.repaint();
    } // hilightPorts

    public void drawSelectedPorts() {
        for ( GObj obj : objects.getSelected() ) {
            obj.setDrawPorts( !obj.isDrawPorts() );
        }
        drawingArea.repaint();
    }
    
    public void print() {
        PrintUtilities.printComponent( this );
    } // print

    public boolean loadScheme( File file ) {
        scheme = null;

        SchemeLoader loader = new SchemeLoader( vPackage );
        
        if ( loader.load( file ) ) {
            if ( loader.getDiagnostics().hasProblems() ) {
                if ( DiagnosticsCollector.promptLoad( this, loader.getDiagnostics(), "Warning: Inconsistent scheme", "scheme" ) ) {
                    scheme = new Scheme( vPackage, loader.getObjectList(), loader.getConnectionList() );
                }
            } else {
                scheme = new Scheme( vPackage, loader.getObjectList(), loader.getConnectionList() );
            }
        } else {
            List<String> msgs = loader.getDiagnostics().getMessages();
            String msg;
            if ( msgs.size() > 0 )
                msg = msgs.get( 0 );
            else
                msg = "An error occured. See the log for details.";

            JOptionPane.showMessageDialog( this, msg, "Error loading scheme", JOptionPane.ERROR_MESSAGE );
        }

        if ( scheme == null )
            return false;

        undoManager.discardAllEdits();
        Editor.getInstance().refreshUndoRedo();
        connections = scheme.getConnections();
        setObjects( scheme.getObjects() );
        for( GObj obj: scheme.getObjects() ) {
            setViewAttributes( obj );
        }
        initClassPainters();
        mListener.setState( State.selection );
        recalcPreferredSize();
        drawingArea.repaint();
        return true;
    } // loadScheme

    public void exportSchemeSpecification( File file ) {
    	
    	String className = file.getName();
    	
    	if( className.lastIndexOf( "." ) > -1 ) {
    		className = className.substring( 0, className.lastIndexOf( "." ) );
    	}
    	
    	String spec = SpecGenFactory.getInstance().getCurrentSpecGen().generateSpec( scheme, className );
    	
        try {
            FileWriter writer = new FileWriter( file );
            
            try {
            	writer.write(spec);
            }
            finally {
            	writer.close();
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    
    public void saveScheme( File file ) {
        OutputStream output = null;
        try {
            // The stream has to be closed explicitly or the file will
            // remain open probably until the next garbage collection.
            output = new FileOutputStream( file );
            StreamResult result = new StreamResult( output );
            SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();

            TransformerHandler th = tf.newTransformerHandler();
            Transformer serializer = th.getTransformer();
            serializer.setOutputProperty( OutputKeys.ENCODING, "UTF-8" );
            serializer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, RuntimeProperties.SCHEME_DTD );
            serializer.setOutputProperty( OutputKeys.INDENT, "yes" );
            th.setResult( result );
            th.startDocument();

            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", "", "package", StringUtil.CDATA, vPackage.getName());

            GObj superClass = scheme.getSuperClass();
            if ( superClass != null ) {
                attrs.addAttribute("", "", "superclass", StringUtil.CDATA, superClass.getName());
            }

            th.startElement("", "", "scheme", attrs);

            for ( GObj obj : objects )
                obj.toXML( th );

            for ( Connection con : connections )
                con.toXML( th );

            th.endElement("", "", "scheme");
            th.endDocument();
            posInfo.setText( "Scheme saved to: " + file.getName() );
        } catch ( Exception e ) {
            e.printStackTrace();
        } finally {
            if ( output != null ) {
                try {
                    output.close();
                } catch ( IOException e ) {
                    db.p( e );
                }
                output = null;
            }
        }
    }

    /**
     * Open the object properties dialog.
     * 
     * @param obj the object
     */
    public void openPropertiesDialog( GObj obj ) {
        ObjectPropertiesEditor.show( obj, this );
    }

    void openClassCodeViewer( String className ) {
        CodeViewer cv = new CodeViewer( className, getWorkDir() );
        cv.setSize( 550, 450 );
        cv.setVisible( true );
    }
    
    /**
     * Opens the object properties dialog on the first selected object if there
     * is one.
     */
    public void openPropertiesDialog() {
        ArrayList<GObj> selected = objects.getSelected();
        if ( selected != null && selected.size() > 0 )
            openPropertiesDialog( selected.get( 0 ) );
    }

    public boolean isGridVisible() {
        return this.showGrid;
    }

    public void setGridVisible( boolean b ) {
        this.showGrid = b;
        drawingArea.repaint();
    }
    
    public void setDrawPorts( boolean b ) {
        
        drawPorts = b;
        
        for ( GObj obj : objects ) {
            obj.setDrawPorts( b );
        }
        
        drawingArea.repaint();
    }

    public void showObjectNames( boolean b ) {
        
        showObjectNames = b;
        
        for ( GObj obj : getObjects() ) {
            obj.setDrawInstanceName( b );
        }
        
        drawingArea.repaint();
    }

    /**
     * @return the showObjectNames
     */
    public boolean isShowObjectNames() {
        return showObjectNames;
    }

    /**
     * @return the drawPorts
     */
    public boolean isDrawPorts() {
        return drawPorts;
    }
    
    class DrawingArea extends JPanel {
        private static final long serialVersionUID = 1L;

        protected void drawGrid( Graphics2D g ) {

            Rectangle vr = g.getClipBounds();

            int step = RuntimeProperties.getGridStep();
            int bx = vr.x + vr.width;
            int by = vr.y + vr.height;
            int unit = (int) Math.ceil( 1.0f / scale );

            g.setColor( Color.lightGray );
            g.setStroke( new BasicStroke( 1.0f / scale ) );

            // draw vertical lines
            for ( int i = ( vr.x + step - unit ) / step * step; i <= bx; i += step )
                g.drawLine( i, vr.y, i, by );

            // draw horizontal lines
            for ( int i = ( vr.y + step - unit ) / step * step; i <= by; i += step )
                g.drawLine( vr.x, i, bx, i );
        }

        @Override
        protected void paintComponent( Graphics g ) {
            Connection rel;
            Graphics2D g2 = (Graphics2D) g;

            g2.setBackground( getBackground() );

            g2.scale( scale, scale );

            Rectangle clip = g2.getClipBounds();
            g2.clearRect( clip.x, clip.y, clip.width, clip.height );

            if ( backgroundImage != null )
                g2.drawImage( backgroundImage, 0, 0, null );

            // grid does not look good at really small scales
            if ( showGrid && scale >= .5f )
                drawGrid( g2 );

            if ( RuntimeProperties.isAntialiasingOn() ) {
                g2.setRenderingHint( java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON );
            }

            for ( GObj obj : objects ) {
                obj.drawClassGraphics( g2, scale );

                if ( enableClassPainter && classPainters != null ) {
                    ClassPainter p = classPainters.get( obj );
                    if ( p != null )
                        p.paint( g2, scale );
                }
            }

            g2.setColor( Color.blue );
            for ( int i = 0; i < connections.size(); i++ ) {
                rel = connections.get( i );
                rel.drawRelation( g2 );
            }

            if ( isConnectionBeingAdded() ) {
                // adding connection, first port connected
                currentCon.drawRelation( g2, mouseX, mouseY );
            } else if ( isRelObjBeingAdded() ) {
                // adding relation object, first port connected

                RelObj obj = (RelObj) currentObj;
                Point point = VMath.getRelClassStartPoint( obj.startPort, mouseX, mouseY );
                obj.setEndPoints( point.x, point.y, mouseX, mouseY );

                currentObj.drawClassGraphics( g2, scale );
            } else if ( currentObj != null && mListener.mouseOver ) {
                currentObj.drawClassGraphics( g2, scale );
            } else if ( mListener.state.equals( State.dragBox ) ) {
                g2.setColor( Color.gray );
                // a shape width negative height or width cannot be drawn
                int rectX = Math.min( mListener.startX, mouseX );
                int rectY = Math.min( mListener.startY, mouseY );
                int width = Math.abs( mouseX - mListener.startX );
                int height = Math.abs( mouseY - mListener.startY );
                g2.drawRect( rectX, rectY, width, height );
            }

            g2.scale( 1.0f / scale, 1.0f / scale );
        }
    }

    /**
     * Update mouse position in info label
     */
    public void setPosInfo( int x, int y ) {
        String message = x + ", " + y;
        
        GObj obj = objects.checkInside( x, y );
        
        if( obj != null ) {
            
            message += " : " + obj.className + " " + obj.getName();
            
            Port port = obj.portContains( x, y );
            
            if( port != null ) {
                message += " : " + port.getType() + " " + port.getName();
            }
        }
        
        posInfo.setText( message );
    }

    /**
     * Sets the background image for the scheme. The preferred size of the
     * drawing area is incremented if necessary to fit the whole image. The
     * current image is removed when <code>image</code> is <code>null</code>.
     * 
     * @param image The image
     */
    public void setBackgroundImage( BufferedImage image ) {
        clearBackgroundImage();
        if ( image != null ) {
            backgroundImage = image;

            int imgw = Math.round( image.getWidth() * scale );
            int imgh = Math.round( image.getHeight() * scale );

            drawAreaSize.height = Math.max( imgh, drawingArea.getHeight() );
            drawAreaSize.width = Math.max( imgw, drawingArea.getWidth() );

            drawingArea.repaint( 0, 0, imgw, imgh );
            drawingArea.revalidate();
        }
    }

    /**
     * @return The current background image or <code>null</code> if there is
     *         no background image.
     */
    public BufferedImage getBackgroundImage() {
        return backgroundImage;
    }

    /**
     * Removes the background image if one is set. Resources will be freed and a
     * <code>repaint()</code> of the image area is requested.
     */
    public void clearBackgroundImage() {
        if ( backgroundImage != null ) {
            int width = Math.round( backgroundImage.getWidth() * scale );
            int height = Math.round( backgroundImage.getHeight() * scale );
            backgroundImage.flush();
            backgroundImage = null;
            drawingArea.repaint( 0, 0, width, height );
        }
    }

    /**
     * @param workDir the workDir to set
     */
    void setWorkDir( String workDir ) {
        this.workDir = workDir;
    }

    /**
     * @return the workDir
     */
    public String getWorkDir() {
        return workDir;
    }

    public float getScale() {
        return scale;
    }

    public void setScale( float scale ) {
        this.scale = scale;
        recalcPreferredSize();
        drawingArea.repaint();
    }

    private void recalcPreferredSize() {
        int maxx = Integer.MIN_VALUE;
        int maxy = Integer.MIN_VALUE;

        if ( backgroundImage != null ) {
            maxx = backgroundImage.getWidth();
            maxy = backgroundImage.getHeight();
        }

        for ( GObj obj : objects ) {
            int tmp = obj.getX() + obj.getRealWidth();
            if ( tmp > maxx )
                maxx = tmp;

            tmp = obj.getY() + obj.getRealHeight();
            if ( tmp > maxy )
                maxy = tmp;
        }

        for( Connection con : getConnections() ) {
            for( Point p : con.getBreakPoints() ) {
                
                if( p.x > maxx ) {
                    maxx = p.x;
                }
                
                if( p.y > maxy ) {
                    maxy = p.y;
                }
            }
        }
        
        drawAreaSize.width = Math.round( scale
                * ( maxx > 0 ? maxx + RuntimeProperties.getGridStep() : drawAreaSize.width / this.scale ) );

        drawAreaSize.height = Math.round( scale
                * ( maxy > 0 ? maxy + RuntimeProperties.getGridStep() : drawAreaSize.height / this.scale ) );

        drawingArea.setPreferredSize( drawAreaSize );
        drawingArea.revalidate();
    }

    public boolean isEnableClassPainter() {
        return enableClassPainter;
    }

    public void setEnableClassPainter( boolean enableClassPainter ) {
        this.enableClassPainter = enableClassPainter;
        drawingArea.repaint();
    }

    private List<Long> m_runners = Collections.synchronizedList( new LinkedList<Long>() );

    public void registerRunner( long id ) {
        m_runners.add( 0, id );
    }

    public void unregisterRunner( long id ) {
        m_runners.remove( id );
    }

    public long getLastProgramRunnerID() {
        return m_runners.size() > 0 ? m_runners.get( 0 ) : 0;
    }

    public void destroy() {
        
        FontChangeEvent.removeFontChangeListener( fontListener );
        
        for ( long id : m_runners ) {
            ProgramRunnerEvent event = new ProgramRunnerEvent( this, id, ProgramRunnerEvent.DESTROY );

            EventSystem.queueEvent( event );
        }
        m_runners.clear();
        drawingArea = null;
    }

    /**
     * Returns a reference to the list of connections.
     * 
     * @return list of connections
     */
    public ConnectionList getConnections() {
        return connections;
    }

    public Connection getConnectionNearPoint( int x, int y ) {
        return connections.nearPoint( x, y );
    }

    public void removeConnection( Connection con ) {
        connections.remove( con );
        undoSupport.postEdit( new DeleteConnectionEdit( this, con ) );
    }

    public void addConnection( Connection con ) {
        connections.add( con );
        undoSupport.postEdit( new AddConnectionEdit( this, con ) );
    }

    public void addConnection( Port port1, Port port2 ) {
        addConnection( new Connection( port1, port2 ) );
    }

    /**
     * Creates a new connection between the port {@code currentCon.begnPort} and
     * the specified port. It is assumed that the ports can be connected and
     * that startAddingConnection() has been called.
     * 
     * @param endPort the end port of the connection
     */
    public void addCurrentConnection( Port endPort ) {
        currentCon.endPort = endPort;
        addConnection( currentCon );
        endPort.setSelected( false );
        currentCon.beginPort.setSelected( false );
        currentCon = null;
        setActionInProgress( false );
    }

    public void clearSelectedConnections() {
        connections.clearSelected();
    }

    public void resizeObjects( int dx, int dy, int corner ) {
        for ( GObj obj : objects ) {
            if ( obj.isSelected() )
                obj.resize( dx, dy, corner );
        }
        objects.updateRelObjs();
        drawingArea.repaint();
    }

    /**
     * Cancels connection adding action and clears the state.
     */
    private void cancelAddingConnection() {
        if ( currentCon != null ) {
            currentCon.beginPort.setSelected( false );
            currentCon = null;
            drawingArea.repaint();
        }

        setActionInProgress( false );
    }

    /**
     * Starts adding a new connection.
     * 
     * @param port the first port of the connection
     */
    void startAddingConnection( Port port ) {
        setActionInProgress( true );
        currentCon = new Connection( port );
    }

    /**
     * Starts adding a new relation object.
     * 
     * @param port the first port of the relation object
     */
    void startAddingRelObject( Port port ) {
        assert currentObj == null;
        assert currentCon == null;
        assert currentPainter == null;

        createAndInitNewObject( State.getClassName( mListener.state ) );

        ( (RelObj) currentObj ).startPort = port;
        port.setSelected( true );

        setActionInProgress( true );
    }

    public void startAddingObject() {
        startAddingObject( mListener.state );
    }

    private void startAddingObject( String state ) {
        assert currentObj == null;
        assert currentCon == null;
        assert currentPainter == null;

        if ( !State.isAddRelClass( state ) )
            createAndInitNewObject( State.getClassName( state ) );
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
            Editor editor = Editor.getInstance();
            editor.deleteAction.setEnabled( !newValue );
            editor.refreshUndoRedo();
        }
    }

    /**
     * Returns true if some non-atomic action that modifies the scheme is in
     * progress. Other actions such as delete and undo that modify the state
     * should not be executed at the same time.
     * 
     * @return true if an action is in progress, false otherwise.
     */
    public boolean isActionInProgress() {
        return actionInProgress;
    }

    /**
     * Cancels the current action that would create a new object or connection.
     */
    public void cancelAdding() {
        if ( currentCon != null )
            cancelAddingConnection();
        else if ( currentObj != null )
            cancelAddingObject();
    }

    private void cancelAddingObject() {
        if ( currentObj != null ) {
            if ( currentObj instanceof RelObj ) {
                RelObj obj = (RelObj) currentObj;
                obj.startPort.setSelected( false );
            }
            currentObj = null;
            currentPainter = null;
            drawingArea.repaint();
        }

        assert currentCon == null;

        setActionInProgress( false );
    }

    /**
     * Returns true if the first port of a connection or relation class is
     * connected and the second port is still disconnected.
     * 
     * @return true if a connection or a relation class is being added, false
     *         otherwise
     */
    public boolean isRelationBeingAdded() {
        return isConnectionBeingAdded() || isRelObjBeingAdded();
    }

    /**
     * Returns true if the first port of a relation class is connected and the
     * second port is still disconnected.
     * 
     * @return true if a relation class is being added, false otherwise
     */
    public boolean isRelObjBeingAdded() {
        return currentObj != null && currentObj instanceof RelObj && ( (RelObj) currentObj ).startPort != null;
    }

    /**
     * Returns true if the first port of a connection is connected and the
     * second port is still disconnected.
     * 
     * @return true if a connection is being added, false otherwise
     */
    public boolean isConnectionBeingAdded() {
        return currentCon != null && currentCon.beginPort != null;
    }

    /**
     * Creates a new instace of the specified visual class. The class is
     * initialized and stored to the filed {@code currentObj}. The new object
     * is also assigned a fresh name.
     * 
     * @param className the name of the visual class
     */
    private void createAndInitNewObject( String className ) {
        PackageClass pClass = vPackage.getClass( className );
        currentObj = pClass.getNewInstance();

        assert currentObj != null;

        currentObj.setName( genObjectName( pClass, currentObj ) );

        setViewAttributes( currentObj );
        
        currentPainter = pClass.getPainterFor( scheme, currentObj );
    }

    private void setViewAttributes( GObj obj ) {
        obj.setDrawPorts( drawPorts );
        obj.setDrawInstanceName( showObjectNames );
    }
    
    /**
     * Returns true if the object can be set as the superclass.
     * 
     * @param obj the object
     * @return true if the object can be set as superclass, false otherwise
     */
    public boolean canBeSetAsSuperClass( GObj obj ) {
        /*
         * allowed connections to superclass
         * 
         * An object can be made a superclass only if it has no connections and
         * there is no superclass yet. The last restriction covers the case when
         * the object itself is already set as a superclass.
         * 
         * if (obj.getConnections().size() > 0) return false;
         */
        for ( GObj o : objects )
            if ( o.isSuperClass() )
                return false;

        return true;
    }

    /**
     * Sets or unsets the specified object as the superclass of the scheme. This
     * method generated an undoable edit and throws a runtime exception if the
     * specified object cannot be safely set as the superclass (for exampe, the
     * object has relations or there is a superclass already).
     * 
     * @param obj the object
     * @param value true to set, false to unset
     */
    public void setAsSuperClass( GObj obj, boolean value ) {
        if ( value && !canBeSetAsSuperClass( obj ) ) {
            throw new IllegalStateException( "The object cannot be set as superclass" );
        }

        obj.setSuperClass( value );

        undoSupport.postEdit( new SetSuperClassEdit( obj, value ) );

        drawingArea.repaint( obj.getX(), obj.getY(), obj.getRealWidth(), obj.getRealHeight() );
    }

    /**
     * Generates and returns a new name for the specified object.
     * 
     * @param pClass the class of the object
     * @param obj the object
     * @return the generated name
     */
    private String genObjectName( PackageClass pClass, GObj obj ) {
        String name;
        do {
            name = pClass.name + "_" + pClass.getNextSerial();
        } while ( !objects.isUniqueName( name, obj ) );
        return name;
    }

    /**
     * Selects the specified object and clears other selections.
     * An attempt is made to bring the object into visible area.
     * @param obj the object to be focused; if obj is null all selections
     * are cleared
     */
    public void focusObject(GObj obj) {
        objects.clearSelected();
        if (obj != null) {
            obj.setSelected(true);

            // Scroll the center of the object as close to the center of
            // the visible area as possible.  To achieve this using
            // scrollRectToVisible() method the coordinates of the object
            // will need to be padded by half of the dimensions of the visible
            // area.  Unlike object dimensions the visible area is independent
            // of the scale.
            Rectangle vr = drawingArea.getVisibleRect();
            vr.x = (int) (obj.getX() * scale + 0.5) - vr.width / 2
                    + (int) (obj.getRealWidth() * scale / 2.0);
            vr.y = (int) (obj.getY() * scale + 0.5) - vr.height / 2
                    + (int) (obj.getRealHeight() * scale / 2.0);
            drawingArea.scrollRectToVisible(vr);
        }
        drawingArea.repaint();
    }

    /**
     * @param objects the objects to set
     */
    void setObjects( ObjectList objects ) {
        this.objects = objects;
    }

    /**
     * @return the objects
     */
    public ObjectList getObjects() {
        return objects;
    }
    
    /**
     * @return the scheme
     */
    public Scheme getScheme() {
        return scheme;
    }
}
