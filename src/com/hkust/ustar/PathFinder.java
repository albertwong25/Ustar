package com.hkust.ustar;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.PriorityQueue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.hkust.ustar.database.DatabaseHelper;

public class PathFinder {
	
	private Context context;
	
	public PathFinder (Context context) {
		this.context = context;
	}
	
	public String getPathString(int sourceNID, int destinationNID) throws IOException
	{	
		Log.d("debb", "In getPathString()");
		
		DatabaseHelper mDatabaseHelper = DatabaseHelper.getInstance(context);
		SQLiteDatabase db = mDatabaseHelper.getDatabase();
		
		Cursor tempSourse=db.rawQuery("SELECT * FROM Node WHERE _id = " + sourceNID, null);
	    tempSourse.moveToFirst();
	    int sFloor = tempSourse.getInt(tempSourse.getColumnIndex("floor"));
	    
	    tempSourse=db.rawQuery("SELECT * FROM Node WHERE _id = " + destinationNID, null);
	    tempSourse.moveToFirst();
	    int tFloor = tempSourse.getInt(tempSourse.getColumnIndex("floor"));
	    tempSourse.close();
	    
	    if ( sFloor == tFloor )
	    {
	    	return getSimplePathString(sourceNID, destinationNID, sFloor);
	    }
	    
	    Cursor node=db.rawQuery("SELECT * FROM Node WHERE floor = " + sFloor, null);
	    int count= node.getCount();
        List<Node> allNodes = new ArrayList<Node>();
		node.moveToFirst(); 
        for(int i = 0; i < count; i++)
        {
        	allNodes.add(new Node(node.getInt(node.getColumnIndex("_id"))));
	        node.moveToNext();
        }  
        node.close();
        
        Log.d("debb", "After allNodes");
	    //Cursor edge=db.rawQuery("SELECT * FROM Edge WHERE floor = " + cFloor, null);
	    //int count_edge= edge.getCount();

	    List<Edge> allEdges;
	    for(int i = 0; i < count; i++)
	    {
	    	allEdges = new ArrayList<Edge>();
	    	Cursor edge=db.rawQuery("SELECT * FROM Edge WHERE floor = " + sFloor + " AND nid_start = " + allNodes.get(i).getNID(), null);
		    edge.moveToFirst();
		    for(int j = 0; j < edge.getCount(); j++)
		    {
	    		int tmpnid = edge.getInt(edge.getColumnIndex("nid_end"));
	    		Node tmpVertex = vertexMatch(allNodes, tmpnid);
	    		allEdges.add(new Edge(tmpVertex,edge.getDouble(edge.getColumnIndex("length"))));
    			edge.moveToNext();
	    	}
    		allNodes.get(i).adjacencies = allEdges;
    		edge.close();
	    }
	    
	    Log.d("debb", "After allEdges");

	    Node source, target; 
	    source = vertexMatch(allNodes, sourceNID);

        //Compute the path that lift choose to near the source
        computePaths(source); // run Dijkstra
        Cursor lift=db.rawQuery("SELECT Facility.nid FROM Facility, Node WHERE Facility.nid = Node._id AND Facility.ftype = 5 AND floor = " + sFloor, null);
        int count_lift = lift.getCount();
        lift.moveToFirst();
        Node upNearestLift = null;
        double upMinWeight = Double.POSITIVE_INFINITY;
        for(int i = 0; i < count_lift; i++)
        {
        	Node tmpVertex = vertexMatch(allNodes, lift.getInt(lift.getColumnIndex("nid")));
        	if ( tmpVertex.minDistance < upMinWeight ) {
        		upMinWeight = tmpVertex.minDistance;
        		upNearestLift = tmpVertex;
        	}
        	lift.moveToNext();
	    }
        lift.close();
        List<Node> upPaths = getShortestPathTo(source,upNearestLift,true);
        String upPathString = "";
        
        for(int i=0; i < upPaths.size(); i++)
    	{
    		if(i == upPaths.size() - 1)
    		{
    			upPathString = upPathString + upPaths.get(i).getNID() + ",";
    		}
    		else 
    		{
    			upPathString = upPathString + upPaths.get(i).getNID() + ",";
    		}
    	}
        Log.d("debb", upPathString);
        Log.d("debb", "After upPathString 1");
        allNodes = renewNodeList(destinationNID);
        target = vertexMatch(allNodes, destinationNID);
	    tempSourse=db.rawQuery("SELECT * FROM Facility WHERE nid = '" + upNearestLift.getNID() + "'", null);
	    tempSourse.moveToFirst();
	    String liftName = tempSourse.getString(tempSourse.getColumnIndex("fname"));
	    Cursor tempLift=db.rawQuery("SELECT Facility.nid AS nid FROM Facility,Node WHERE Facility.nid = Node._id AND floor = " + tFloor + " AND fname = '" + liftName + "'", null);
	    tempLift.moveToFirst();
	    upNearestLift = vertexMatch(allNodes, tempLift.getInt(tempLift.getColumnIndex("nid")));
	    tempSourse.close();
	    tempLift.close();
	    
        computePaths(upNearestLift);
        upPaths = getShortestPathTo(upNearestLift, target,true);
        upMinWeight += target.minDistance;

        for(int i=0; i < upPaths.size(); i++)
    	{
    		if(i == upPaths.size() - 1)
    		{
    			upPathString = upPathString + upPaths.get(i).getNID();
    		}
    		else 
    		{
    			upPathString = upPathString + upPaths.get(i).getNID() + ",";
    		}
    	}
        Log.d("debb", upPathString);
        Log.d("debb", "After upPathString 2");

        //Compute the path that lift choose to near the target
        allNodes = renewNodeList(destinationNID);
        target = vertexMatch(allNodes, destinationNID);
        computePaths(target); // run Dijkstra
        lift=db.rawQuery("SELECT Facility.nid FROM Facility, Node WHERE Facility.nid = Node._id AND Facility.ftype = 5 AND floor = " + tFloor, null);
        count_lift = lift.getCount();
        lift.moveToFirst();
        Node butNearestLift = null;
        double butMinWeight = Double.POSITIVE_INFINITY;
        for(int i = 0; i < count_lift; i++)
        {
        	Node tmpVertex = vertexMatch(allNodes, lift.getInt(lift.getColumnIndex("nid")));
        	if ( tmpVertex.minDistance < butMinWeight ) {
        		butMinWeight = tmpVertex.minDistance;
        		butNearestLift = tmpVertex;
        	}
        	lift.moveToNext();
	    }
        lift.close();
        List<Node> butPaths = getShortestPathTo(target,butNearestLift,false);
        String butPathString = "";

        Log.d("debb", "After butPathString 1");
        allNodes = renewNodeList(sourceNID);

	    tempSourse=db.rawQuery("SELECT * FROM Facility WHERE nid = '" + butNearestLift.getNID() + "'", null);
	    tempSourse.moveToFirst();
	    liftName = tempSourse.getString(tempSourse.getColumnIndex("fname"));
	    tempLift=db.rawQuery("SELECT Facility.nid AS nid FROM Facility,Node WHERE Facility.nid = Node._id AND floor = " + sFloor + " AND fname = '" + liftName + "'", null);
	    tempLift.moveToFirst();
	    butNearestLift = vertexMatch(allNodes, tempLift.getInt(tempLift.getColumnIndex("nid")));
	    tempSourse.close();
	    tempLift.close();
        
	    source = vertexMatch(allNodes, sourceNID);
	    computePaths(butNearestLift);
	    List<Node> butPaths2 = getShortestPathTo(butNearestLift, source, false);
        butMinWeight += source.minDistance;

        for(int i=0; i < butPaths2.size(); i++)
    	{
    		if(i == butPaths2.size() - 1)
    		{
    			butPathString = butPathString + butPaths2.get(i).getNID() + ",";
    		}
    		else 
    		{
    			butPathString = butPathString + butPaths2.get(i).getNID() + ",";
    		}
    	}
        
        Log.d("debb", butPathString);
        
        for(int i=0; i < butPaths.size(); i++)
    	{
    		if(i == butPaths.size() - 1)
    		{
    			butPathString = butPathString + butPaths.get(i).getNID();
    		}
    		else 
    		{
    			butPathString = butPathString + butPaths.get(i).getNID() + ",";
    		}
    	}
        
        Log.d("debb", butPathString);
        Log.d("debb", "After butPathString 2");
        
        if ( upMinWeight > butMinWeight )
        	return butPathString;
        else
        	return upPathString;
	}
	
	public String getSimplePathString(int sourceNID, int destinationNID, int cFloor) throws IOException
	{	
		Log.d("debb", "In getSimplePathString()");
		
		DatabaseHelper mDatabaseHelper = DatabaseHelper.getInstance(context);
		SQLiteDatabase db = mDatabaseHelper.getDatabase();

	    Cursor node=db.rawQuery("SELECT * FROM Node WHERE floor = " + cFloor, null);
	    int count= node.getCount();
        List<Node> allNodes = renewNodeList(sourceNID);
        node.close();
        
        Node source, target; 
        source = vertexMatch(allNodes, sourceNID);
        target = vertexMatch(allNodes, destinationNID);

        computePaths(source); // run Dijkstra
        List<Node> paths = getShortestPathTo(source,target,true);
    	
    	String pathString = "";
    	
    	for(int i=0; i < paths.size(); i++)
    	{
    		if(i == paths.size() - 1)
    		{
    			pathString = pathString + paths.get(i).getNID();
    		}
    		else 
    		{
    			pathString = pathString + paths.get(i).getNID() + ",";
    		}
    	}
    	
    	return pathString; 
	    
	}
	
	/**
	 * Compute shortest path using Djikstra's shortest path algorithm
	 */
    public static void computePaths(Node source)
    {
        source.minDistance = 0;
        PriorityQueue<Node> vertexQueue = new PriorityQueue<Node>();
        vertexQueue.add(source);

	    while (!vertexQueue.isEmpty()) {
	        Node u = vertexQueue.poll();
	            // Visit each edge exiting u
	            for (Edge e : u.adjacencies)
	            {
	                Node v = e.target;
	                double weight = e.weight;
	                double distanceThroughU = u.minDistance + weight;
			        if (distanceThroughU < v.minDistance) {
			            vertexQueue.remove(v);
			            v.minDistance = distanceThroughU ;
			            v.previous = u;
			            u.next = v; 
			            vertexQueue.add(v);
			        }
	            }
        	}
    }

    public static List<Node> getShortestPathTo(Node source, Node target, boolean Upoath)
    {
        List<Node> path = new ArrayList<Node>();
        for (Node vertex = target; vertex != source.previous; vertex = vertex.previous)
            path.add(vertex);

        if ( Upoath )
        	Collections.reverse(path);
        
        return path;
    }
    /*
    public static Node getSrcDest(List<Node> path, int nid)
    {
        Node node = null;// = new Vertex(source.name);
        for(int i =0; i < path.size(); i++)
        {
        	if(Integer.valueOf(path.get(i).getNID()) == nid)
        	{
        		node = path.get(i);
        	}
        }
        return node;
    }
    */
    
    public static Node vertexMatch(List<Node> node, int nid)
    {
    	for(int i = 0; i < node.size(); i++)
    	{
    		if(nid == Integer.valueOf(node.get(i).getNID()))
    		{
    			return node.get(i);
   			}
    	}
    	return null; 
    }
    public List<Node> renewNodeList(int sourceNID) throws IOException
    {
    	Log.d("debb", "In renewNodeList()");
    	DatabaseHelper mDatabaseHelper = DatabaseHelper.getInstance(this.context);
		SQLiteDatabase db = mDatabaseHelper.getDatabase();
		Cursor tempSourse=db.rawQuery("SELECT * FROM Node WHERE _id = '" + sourceNID + "'", null);
	    tempSourse.moveToFirst();
	    int cFloor = tempSourse.getInt(tempSourse.getColumnIndex("floor"));
        tempSourse.close();
	    
	    Cursor node=db.rawQuery("SELECT * FROM Node WHERE floor = '" + cFloor + "'", null);
	    int count= node.getCount();
        List<Node> allNodes = new ArrayList<Node>();
		node.moveToFirst(); 
        for(int i = 0; i < count; i++)
        {
        	allNodes.add(new Node(node.getInt(node.getColumnIndex("_id"))));
	        node.moveToNext() ;
        }
        node.close();
        
        Log.d("debb", "After allNodes");
        
        //Cursor edge=db.rawQuery("SELECT * FROM Edge WHERE floor = '" + cFloor + "'", null);
	    //int count_edge= edge.getCount();
        
	    List<Edge> allEdges;
	    for(int i = 0; i < count; i++)
	    {
	    	allEdges = new ArrayList<Edge>();
	    	Cursor edge=db.rawQuery("SELECT * FROM Edge WHERE floor = " + cFloor + " AND nid_start = " + allNodes.get(i).getNID(), null);
		    edge.moveToFirst();
		    for(int j = 0; j < edge.getCount(); j++)
		    {
	    		int tmpnid = edge.getInt(edge.getColumnIndex("nid_end"));
	    		Node tmpVertex = vertexMatch(allNodes, tmpnid);
	    		allEdges.add(new Edge(tmpVertex,edge.getDouble(edge.getColumnIndex("length"))));
    			edge.moveToNext();
	    	}
    		allNodes.get(i).adjacencies = allEdges;
    		edge.close();
	    }
	    
	    Log.d("debb", "End renewNodeList()");
	    
        return allNodes;
    }
}