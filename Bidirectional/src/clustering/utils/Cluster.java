package clustering.utils;

import com.aliasi.classify.PrecisionRecallEvaluation;
import com.aliasi.cluster.HierarchicalClusterer;
import com.aliasi.cluster.ClusterScore;
import com.aliasi.cluster.CompleteLinkClusterer;
import com.aliasi.cluster.SingleLinkClusterer;
import com.aliasi.cluster.Dendrogram;
import com.aliasi.stats.Statistics;
import com.aliasi.util.Counter;
import com.aliasi.util.Distance;
import com.aliasi.util.Files;
import com.aliasi.util.ObjectToCounterMap;
import com.aliasi.util.Strings;

import java.lang.Math;

import com.aliasi.cluster.*;
import com.aliasi.tokenizer.*;
import com.google.common.primitives.Doubles;

import java.io.*;
import java.util.*;

import org.apache.commons.lang.ArrayUtils;

public class Cluster {
	public static TreeSet<Set<Tree>> clusterRank(Set<Set<Tree>> clusters,int k) {
		Set<Set<Tree>> newSet = null;
		switch(k){
		case 0:
			newSet = new TreeSet<Set<Tree>>(new HighestComparator());
			newSet.addAll(clusters);
			break;
		case 1:
			newSet = new TreeSet<Set<Tree>>(new LowestComparator());
			newSet.addAll(clusters);
			break;
		case 2:
			newSet = new TreeSet<Set<Tree>>(new AverageComparator());
			newSet.addAll(clusters);
			break;
		case 3:
			newSet = new TreeSet<Set<Tree>>(new LargestTreeComparator());
			newSet.addAll(clusters);
			break;
		}
		return (TreeSet)newSet;
	}

    
    public static Set<Set<Tree>> cluster(Set<Tree> treeSet){//No 'k' required... ,int k) {

    	double maxDistance=1.0D;

        // eval clusterers
        HierarchicalClusterer<Tree> clClusterer
            = new CompleteLinkClusterer<Tree>(maxDistance,COSINE_DISTANCE);
        System.out.println("Now I am calculating dendrogram for "+treeSet.size()+ "trees.");
        Dendrogram<Tree> completeLinkDendrogram
            = clClusterer.hierarchicalCluster(treeSet);
        System.out.println(completeLinkDendrogram.prettyPrint());
        
        double CHval=0.0;
        ArrayList<Double> Charr=new ArrayList<Double>();
        int optK=1;
        int maxSize=11; 
        System.out.println("The possible sizes being examined:"+maxSize);
        
        for (int kk = 2; kk <= maxSize; ++kk)
        {
        	Set<Set<Tree>> clKClusteringtmp = completeLinkDendrogram.partitionK(kk);
        	System.out.println("Examining:"+kk);
        	double W= completeLinkDendrogram.withinClusterScatter(kk, COSINE_DISTANCE);
        	double T= totalScatter(clKClusteringtmp);
        	double B=T-W;
        	double CH = B*(treeSet.size()-kk)/(W*(kk-1));
        	Charr.add(CH);
        	if(CH>CHval){
        		CHval=CH;
        		optK=kk;
        	}
        	
        }
        Set<Set<Tree>> clKClustering = completeLinkDendrogram.partitionK(optK);
        System.out.println(optK + "  " + clKClustering);
        System.out.println("CHVals:"+Charr.toString());
        System.out.println(" --------------------------------------------------------");
        return clKClustering;
    }



    private static double totalScatter(Set<Set<Tree>> clKClusteringtmp) {
    	double Tval=0;
    	Iterator<Set<Tree>> treeiter=clKClusteringtmp.iterator();
    	ArrayList<Tree> allTrees = new ArrayList<Tree>();
    	while(treeiter.hasNext()){
    		allTrees.addAll(treeiter.next());
    	}
    	for(int i=0;i<allTrees.size();i++){
    		Tree tI=allTrees.get(i);
    		for(int k=0;k<allTrees.size();k++){
    			Tree tK=allTrees.get(k);
    			if(i==k)
    				continue;	
    			double dVal=0.0;
    			dVal=COSINE_DISTANCE.distance(tI, tK);
    			Tval=Tval+dVal;
    		}
    	}
		return Tval/2;
	}



	static final Distance<Tree> COSINE_DISTANCE
        = new Distance<Tree>() {
            public double distance(Tree tree1, Tree tree2) {

            	 if(tree1.vectorRelationTerms.isEmpty() && !tree2.vectorRelationTerms.isEmpty()) 
            	 { 
   				  for(int i=0;i< tree2.vectorRelationTerms.size();i++) 
   				  {
   					  tree1.vectorRelationTerms.add(i,0.0); 
   				   } 
   			  	}
            	
            	 if(tree2.vectorRelationTerms.isEmpty() && !tree1.vectorRelationTerms.isEmpty()) 
            	 { 
   				  for(int i=0;i< tree1.vectorRelationTerms.size();i++) 
   				  {
   					  tree2.vectorRelationTerms.add(i,0.0); 
   				   } 
   			  	}
            	 

            	 if(tree1.vectorEntityTerms.isEmpty() && !tree2.vectorEntityTerms.isEmpty()) 
            	 { 
   				  for(int i=0;i< tree2.vectorEntityTerms.size();i++) 
   				  {
   					  tree1.vectorEntityTerms.add(i,0.0); 
   				   } 
   			  	}
            	
            	 if(tree2.vectorEntityTerms.isEmpty() && !tree1.vectorEntityTerms.isEmpty()) 
            	 { 
   				  for(int i=0;i< tree1.vectorEntityTerms.size();i++) 
   				  {
   					  tree2.vectorEntityTerms.add(i,0.0); 
   				   } 
   			  	}
            	 
                double[] array1=Doubles.toArray(tree1.vectorEntityTerms);
                double[] array2=Doubles.toArray(tree2.vectorEntityTerms);
            	 
            	double[] array3=Doubles.toArray(tree1.vectorRelationTerms);
            	double[] array4=Doubles.toArray(tree2.vectorRelationTerms);
            	
            	return (0.5*(Statistics.jsDivergence(array1, array2)+Statistics.jsDivergence(array3, array4)));
            }
        };
        public static
    	<T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
    		List<T> list = new ArrayList<T>(c);
    		java.util.Collections.sort(list);
    		return list;
    	}

    	public static class HighestComparator implements Comparator<Set<Tree>> {  

    		public int compare(Set<Tree> _firstPerson, Set<Tree> _secondPerson) {  
    			List<Tree> list1 = asSortedList(_firstPerson);
    			List<Tree> list2 = asSortedList(_secondPerson);

    			return(list1.get(0).compareTo(list2.get(0)));			  
    		}  

    	} 
    
    	
    	public static class LowestComparator implements Comparator<Set<Tree>> {  

    		public int compare(Set<Tree> _firstPerson, Set<Tree> _secondPerson) {  
    			List<Tree> list1 = asSortedList(_firstPerson);
    			List<Tree> list2 = asSortedList(_secondPerson);
    			int l1=list1.size()-1;
    			int l2=list2.size()-1;
    			return(list1.get(l1).compareTo(list2.get(l2)));			  
    		}  

    	} 
    	
    	public static class AverageComparator implements Comparator<Set<Tree>> {  

    		public int compare(Set<Tree> _firstPerson, Set<Tree> _secondPerson) {  
    			List<Tree> list1 = asSortedList(_firstPerson);
    			List<Tree> list2 = asSortedList(_secondPerson);

    			return(clusterAverage(list1,list2));			  
    		}

			private int clusterAverage(List<Tree> list1, List<Tree> list2) {
				Integer av1=0;
				Integer av2=0;
				Iterator iter1=list1.iterator();
				while(iter1.hasNext()){
					Tree tmp=(Tree)iter1.next();
					av1+=tmp.rank;
				}
				Iterator iter2=list2.iterator();
				while(iter2.hasNext()){
					Tree tmp=(Tree)iter2.next();
					av2+=tmp.rank;
				}
				int retval = av1.compareTo(av2);
				if(retval==0)
					retval=-1;
				return (retval);
				
			}  

    	} 
    	
    	public static class LargestTreeComparator implements Comparator<Set<Tree>> {  

    		public int compare(Set<Tree> _firstPerson, Set<Tree> _secondPerson) {  
    			List<Tree> list1 = asSortedList(_firstPerson);
    			List<Tree> list2 = asSortedList(_secondPerson);

    			return(largestTree(list1,list2));			  
    		}

			private int largestTree(List<Tree> list1, List<Tree> list2) {
				Integer largest1=-1;
				Integer largest2=-1;
				
				Iterator iter1=list1.iterator();
				while(iter1.hasNext()){
					Tree tmp=(Tree)iter1.next();
					if(tmp.treeSize>largest1){
						largest1=tmp.treeSize;
					}
				}
				Iterator iter2=list2.iterator();
				while(iter2.hasNext()){
					Tree tmp=(Tree)iter2.next();
					if(tmp.treeSize>largest2){
						largest2=tmp.treeSize;
					}
				}
				int retval=largest1.compareTo(largest2);
				
				if(retval==0)
					retval=-1;
				return (retval);
			}  

    	}

		public static double distanceBetweenTrees(Tree tI, Tree tK) {
			return COSINE_DISTANCE.distance(tI, tK);
			
		} 
}
