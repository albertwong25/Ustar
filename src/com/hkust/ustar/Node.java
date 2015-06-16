package com.hkust.ustar;

import java.util.List;

public class Node implements Comparable<Node>
{
    public final int nid; 
    public List<Edge> adjacencies;
    public double minDistance = Double.POSITIVE_INFINITY;
    public Node previous, next;
    
    public Node(int nidval)
    {
    	nid = nidval;
    }
    
    public int getNID()
    {
    	return nid;	
    }
    
    @Override
	public int compareTo(Node other)
    {
        return Double.compare(minDistance, other.minDistance);
    }
}