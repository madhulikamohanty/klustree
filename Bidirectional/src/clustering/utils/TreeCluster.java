package clustering.utils;

import java.util.ArrayList;

public class TreeCluster {
	public static int topEntityK=5;
	public ArrayList<Double> allEntityValues;
	public double[] topEntityValues;
	public String[] topEntityTerms;

	public static int topRelationK=5;
	public ArrayList<Double> allRelationValues;
	public double[] topRelationValues;
	public String[] topRelationTerms;
	
	public TreeCluster()
	{
		allEntityValues=new ArrayList<Double>();
		topEntityValues=new double[topEntityK];
		topEntityTerms=new String[topEntityK];	
		
		allRelationValues=new ArrayList<Double>();
		topRelationValues=new double[topRelationK];
		topRelationTerms=new String[topRelationK];	
	}
}
