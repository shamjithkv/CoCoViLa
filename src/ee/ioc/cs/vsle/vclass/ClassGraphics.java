package ee.ioc.cs.vsle.vclass;

import ee.ioc.cs.vsle.graphics.Shape;
import ee.ioc.cs.vsle.graphics.Text;

import java.io.Serializable;
import java.util.ArrayList;
import java.awt.*;

public class ClassGraphics implements Serializable {

	ArrayList shapes = new ArrayList();
	public double angle = 0.0;
	public int boundX;
	public int boundY;
	public int boundWidth;
	public int boundHeight;
//	PackageClass packageClass;
	public boolean showFields = false;
	public boolean relation = false;

	/**
	 * Set bounds of the graphical object.
	 * @param x int - x coordinate of the object starting corner.
	 * @param y int - y coordinate of the object starting corner.
	 * @param width int - object width (difference between starting and ending x coordinates).
	 * @param height int - object height (difference between starting and ending y coordinates).
	 */
	public void setBounds(int x, int y, int width, int height) {
		boundX = x;
		boundY = y;
		boundWidth = width;
		boundHeight = height;
	} // setBounds

	/**
	 * Add a new shape to the list of shapes.
	 * @param s Shape - shape to be added to the list of shapes.
	 */
	public void addShape(Shape s) {
		shapes.add(s);
	} // addShape

	/**
	 * Returns the width of the object (the difference between the object's beginning and
	 * end x coordinates).
	 * @return int - width of the object.
	 */
	public int getWidth() {
		return boundWidth;
	} // getWidth

	/**
	 * Returns the height of the object (the difference between the object's beginning and
	 * end y coordinates).
	 * @return int - height of the object.
	 */
	public int getHeight() {
		return boundHeight;
	} // getHeight

	/**
	 * Draw shape.
	 * @param xPos int - shape x coordinate value. Specifies the point to start the shape drawing from.
	 * @param yPos int - shape y coordinate value. Specifies the point to start the shape drawing from.
	 * @param Xsize float -
	 * @param Ysize float -
	 * @param g Graphics -
	 */
	void draw(int xPos, int yPos, float Xsize, float Ysize, Graphics g) {
		Shape s;
        Graphics2D g2 = (Graphics2D) g;

		if (relation) {
			g2.translate(xPos, yPos);
			g2.rotate(angle);
			g2.translate(-1*(xPos), -1*(yPos));
		}

		for (int i = 0; i < shapes.size(); i++) {
			s = (Shape) shapes.get(i);
			s.draw(xPos, yPos, Xsize, Ysize, g);
		}
		if (relation) {
			g2.translate(xPos, yPos);
			g2.rotate(-1*angle);
			g2.translate(-1*(xPos), -1*(yPos));
		}

	} // draw

	void drawSpecial(int xPos, int yPos, float Xsize, float Ysize, Graphics g, String name, String value) {
		Shape s;
        Graphics2D g2 = (Graphics2D) g;

		if (relation) {
			g2.translate(xPos, yPos);
			g2.rotate(angle);
			g2.translate(-1*(xPos), -1*(yPos));
		}

		for (int i = 0; i < shapes.size(); i++) {
			s = (Shape) shapes.get(i);
			if (s instanceof Text)
				((Text)s).drawSpecial(xPos, yPos, Xsize, Ysize, g, name, value);
			else
				s.draw(xPos, yPos, Xsize, Ysize, g);
		}
		if (relation) {
			g2.translate(xPos, yPos);
			g2.rotate(-1*angle);
			g2.translate(-1*(xPos), -1*(yPos));
		}

	} // draw


}
