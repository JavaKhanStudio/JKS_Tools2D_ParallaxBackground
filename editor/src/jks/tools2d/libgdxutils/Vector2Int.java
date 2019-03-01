package jks.tools2d.libgdxutils;

import com.badlogic.gdx.math.Vector3;

public class Vector2Int 
{
	public int x, y ;

	public Vector2Int()
	{x = 0; y = 0;}
	
	public Vector2Int(int X, int Y)
	{x = X ; y = Y;}
	
	public Vector2Int(Vector3 pos)
	{x = (int) pos.x ; y = (int) pos.y ;}
	
	@Override
	public String toString()
	{return x + "," + y ;}
	
	public int getX() 
	{return x;}

	public void setX(int x) 
	{this.x = x;}

	public int getY() 
	{return y;}

	public void setY(int y) 
	{this.y = y;}
}
