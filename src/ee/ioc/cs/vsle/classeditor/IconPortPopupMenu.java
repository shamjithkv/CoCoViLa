package ee.ioc.cs.vsle.classeditor;

import ee.ioc.cs.vsle.editor.Menu;

import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

public class IconPortPopupMenu
	extends JPopupMenu
	implements ActionListener {

  /**
   * Port, for which the properties are set. Selected in the ClassEditor application.
   */
  IconPort port;

  /**
   * Reference to the ClassEditor application.
   */
  ClassEditor editor;

  /**
   * Class constructor.
   * @param port IconPort - port reference. Selected in the IconEditor application.
   * @param editor IconEditor - IconEditor application reference.
   */
  IconPortPopupMenu(IconPort port, ClassEditor editor) {
	super();
	this.port = port;
	this.editor = editor;
	
	
	
	this.add(ClassEditor.getInstance().deleteAction);

	this.addSeparator();
	JMenuItem menuItem = new JMenuItem(Menu.PROPERTIES, KeyEvent.VK_P);
	menuItem.addActionListener(this);
	menuItem.setActionCommand(Menu.PROPERTIES);
	this.add(menuItem);
	
	JMenuItem menuItemOP = new JMenuItem(Menu.EDIT_OPEN);
	menuItemOP.addActionListener(this);
	menuItemOP.setActionCommand(Menu.EDIT_OPEN);
	this.add(menuItemOP);
	
	JMenuItem menuItemCP = new JMenuItem(Menu.EDIT_CLOSED);
	menuItemCP.addActionListener(this);
	menuItemCP.setActionCommand(Menu.EDIT_CLOSED);
	this.add(menuItemCP);
  } // IconPortPopupMenu constructor

  /**
   * Action event listener method.
   * @param e ActionEvent - action event performed.
   */
  public void actionPerformed(ActionEvent e) {
	  if ( e.getActionCommand().equals( Menu.PROPERTIES ) ) {
		  new PortPropertiesDialog(editor, port).setVisible( true );  
	  }
	  else if ( e.getActionCommand().equals( Menu.EDIT_OPEN ) ) {
	    	ClassEditor.getInstance().loadPortGraphicClass(true, port);
	    }     	  
	  else if ( e.getActionCommand().equals( Menu.EDIT_CLOSED ) ) {
	    	ClassEditor.getInstance().loadPortGraphicClass(false, port);
	    }     	  
  } // actionPerformed

}