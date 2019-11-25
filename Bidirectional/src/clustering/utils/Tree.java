
package clustering.utils;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

import iitb.banks.datasource.*;
import iitb.banks.search.*;

import java.sql.Blob;
import java.nio.ByteBuffer;

public class Tree implements Comparable<Tree>,Serializable {
	public MyTreeNode root;
	public float edgeScore;
	public float nodeScore;
	public float totalScore;
	public long timestamp;
	public int seqnum;
	public int fwd, treeSize;
	public int leaf2leafMax;
	public float origEdgeScore;
	public ArrayList<Entity> entities;
	public ArrayList<Relation> relations;
	public ArrayList<Double> vectorEntityTerms;
	public ArrayList<Double> vectorRelationTerms;
	public static Hashtable<String, ArrayList<Double>> vectorCache;
	public int entityCount, relationCount;
	public int rank;
	public int cluster;

	static
	{
		vectorCache=new Hashtable<String, ArrayList<Double>>();
	}

	public Tree(MyTreeNode root) {
		this.root = root;
		this.edgeScore = -1;
		this.nodeScore = -1;
		this.totalScore = -1;
		this.entities = new ArrayList<Entity>();
		this.relations = new ArrayList<Relation>();
		this.vectorEntityTerms = new ArrayList<Double>();
		this.vectorRelationTerms = new ArrayList<Double>();
		fwd = -1;
		treeSize = -1;
		calcMaxs(root);
		int max1, max2;
		max1 = max2 = -1;
		for (MyTreeNode t = root.firstChild; t != null; t = t.nextSibling) {
			if (t.maxdepth > max1) {
				max2 = max1;
				max1 = t.maxdepth;
			} else if (t.maxdepth > max2)
				max2 = t.maxdepth;
		}
		if (max2 != -1)
			leaf2leafMax = max1 + max2 + 2;
		else
			leaf2leafMax = max1 + 1;

	}

	public Tree(MyTreeNode root, float edgeScore, float nodeScore,
			float totalScore)

	{
		this.root = root;
		this.edgeScore = edgeScore;
		this.nodeScore = nodeScore;
		this.totalScore = totalScore;
		fwd = -1;
		treeSize = -1;
		this.entities = new ArrayList<Entity>();
		this.relations = new ArrayList<Relation>();
		this.vectorEntityTerms = new ArrayList<Double>();
		this.vectorRelationTerms = new ArrayList<Double>();
	}

	public void calcMaxs(MyTreeNode mtn) {

		if (mtn.firstChild == null) {
			mtn.maxdepth = 0;
			return;
		} else {
			int max = -1;
			for (MyTreeNode t = mtn.firstChild; t != null; t = t.nextSibling) {
				calcMaxs(t);
				if (t.maxdepth > max)
					max = t.maxdepth;
			}
			mtn.maxdepth = max + 1;
		}

	}

	public String answerPattern(DataSource ds, SearchParam sp) {
		StringBuffer sb = new StringBuffer();
		ansPat(root, sb, ds, sp);
		return sb.toString();
	}

	protected Statement stmt;

	
	 public void assignFeatureVectors(Object item,DataSource db) 
	 {
	  ResultSet rs=null; 
	  Blob vectorBlob=null;
	  String table_name=null,column_name=null,class_name=null,obj_name=null;
	  
	  if(item.getClass()==Entity.class) 
	  { 
		  table_name="banks_entity_vectors";
		  column_name="entity_name"; 
		  class_name="Entity";
		  obj_name=((Entity)item).name; 
	  } 
	  else 
	  {
		  table_name="banks_relation_vectors"; 
		  column_name="relation_name";
		  class_name="Relation"; 
		  obj_name=((Relation)item).name; 
	  } 
	  try {
		  stmt=db.createStatement(); 
		  obj_name=obj_name.replaceAll("'", "''");
		  rs=stmt
		  	.executeQuery("select value from "+table_name+" where "+column_name
		  			+"='"+obj_name+"'"); 
		  rs.next(); 
		  			
		  if(class_name=="Entity") 
		  {
	  
			  vectorBlob=rs.getBlob("value"); 
			  long no_bytes = vectorBlob.length();
		  
			  if(vectorEntityTerms.isEmpty()) 
			  { 
				  for(int i=1;i<no_bytes;i+=8) 
				  {
					  this.vectorEntityTerms.add(i/8,0.0); 
				  } 
			  } 
		  
			  for(int i=1;i<no_bytes;i+=8) 
			  {
				  
				  Double value=ByteBuffer.wrap(vectorBlob.getBytes(i, 8)).getDouble();
				  Double oldValue=this.vectorEntityTerms.get(i/8);
				  this.vectorEntityTerms.set(i/8,oldValue+value); 
			  } 
		  } 
		  
		  else 
		  {
			  vectorBlob=rs.getBlob("value"); long no_bytes = vectorBlob.length();
			  
			  if(vectorRelationTerms.isEmpty()) 
			  { 
				  for(int i=1;i<no_bytes;i+=8) 
				  {
					  this.vectorRelationTerms.add(i/8,0.0); 
				   } 
			  }
			  
			  for(int i=1;i<no_bytes;i+=8) { 
				  Double value=ByteBuffer.wrap(vectorBlob.getBytes(i, 8)).getDouble(); 
				  Double oldValue=this.vectorRelationTerms.get(i/8);
				  this.vectorRelationTerms.set(i/8,oldValue+value); 
			  } 
		  } 
		  
		  stmt.close(); 
	  }
	  catch (SQLException e)
		  System.out.println(obj_name); e.printStackTrace(); }
	  
	  }

	public void addEntitiesRelations(DataSource ds, MyTreeNode node,
			String parentTable) {
		Hashtable<String, String> myEntities = ds.G
				.getAllEntities(node.vertexIndex);

		// get the table name and delete the entry
		String TableName = myEntities.get("banks_table_name");
		myEntities.remove("banks_table_name");

		double entityValue = 0.0;
		double relationValue = 0.0;

		for (Map.Entry<String, String> myEntity : myEntities.entrySet()) {
			// create new entity for each new column
			Entity entity = new Entity();
			entity.name = myEntity.getValue();
			
			assignFeatureVectors(entity, ds);
			entities.add(entity);

			// create new relations from other entities of same node
			for (Map.Entry<String, String> otherEntity : myEntities.entrySet()) {
				if (otherEntity != myEntity) {
					Relation relation = new Relation();
					relation.name = TableName + "#" + TableName + "#"
							+ otherEntity.getKey();

					relations.add(relation);
					assignFeatureVectors(relation, ds);

				}
			}

			if (parentTable == null)
				continue;

			// create new relation from the parent
			Relation relation = new Relation();
			relation.name = parentTable + "#" + TableName + "#"
					+ myEntity.getKey();

			relations.add(relation);
		

			assignFeatureVectors(relation, ds);

		}

		if (node.firstChild != null) {
			addEntitiesRelations(ds, node.firstChild, TableName);
		}

		if (node.nextSibling != null) {
			addEntitiesRelations(ds, node.nextSibling, parentTable);
		}
	}

	public void computeEntityVector() {
		entityCount = vectorEntityTerms.size();
		double multiplier = 1.0 / entityCount;
		double value = 0.0;

		for (int i = 0; i < entityCount; i++) {
			value = vectorEntityTerms.get(i);
			this.vectorEntityTerms.set(i, value * multiplier);
		}

		entities.clear();
	}

	public void computeRelationVector() {
		relationCount = vectorRelationTerms.size();
		double multiplier = 1.0 / relationCount;
		double value = 0.0;

		for (int i = 0; i < relationCount; i++) {
			value = vectorRelationTerms.get(i);
			this.vectorRelationTerms.set(i, value * multiplier);
		}
	}

	private static String getKeyword(SearchParam sp, int nodeId) {
		String word = "";
		for (int i = 0; i < sp.kws.size(); i++) {
			Keyword kw = (Keyword) sp.kws.get(i);
			if (!kw.resolved && kw.origin != null) {
				for (int j = 0; j < kw.origin.size(); j++)
					if (kw.origin.get(j) == nodeId)
						word += kw + " ";
			}
		}
		if (word.equals(""))
			return "[" + nodeId + "]";
		return word;
	}

	private static void ansPat(MyTreeNode t, StringBuffer sb, DataSource ds,
			SearchParam sp) {
		if (t.firstChild == null) {
			sb.append(getKeyword(sp, t.vertexIndex) + "(path ");
			ancestorPath(t, sb, ds);
			sb.append(") ");
		} else {
			for (MyTreeNode cur = t.firstChild; cur != null; cur = cur.nextSibling)
				ansPat(cur, sb, ds, sp);
		}
	}

	private static void ancestorPath(MyTreeNode t, StringBuffer sb,
			DataSource ds) {
		if (t == null)
			return;
		sb.append("\\" + ds.G.getTableId(t.vertexIndex));
		ancestorPath(t.parent, sb, ds);
	}

	public String toString() {
		return "Tree " + rank;
	}

	public int compareTo(Tree n) {
		return ((Integer) rank).compareTo(n.rank);

	}
}
