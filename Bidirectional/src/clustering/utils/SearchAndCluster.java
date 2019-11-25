package clustering.utils;

import iitb.banks.datasource.DataSource;
import iitb.banks.search.BackwardExpandingRI;
import iitb.banks.search.BidirectionalRI;
import iitb.banks.search.BufferedRI;
import iitb.banks.search.Mult_BackwardExpandingRI;
import iitb.banks.search.RandomWalkRI;
import iitb.banks.search.ResultIterator;
import iitb.banks.search.SearchParam;
import iitb.banks.search.TwigRI;
import iitb.banks.util.ActivationTreeScorer;
import iitb.banks.util.Config;
import iitb.banks.util.ConventionalTreeScorer;
import iitb.banks.util.FileEdit;
import iitb.banks.util.Logger;
import iitb.banks.util.PrettyResultPrinter;
import iitb.banks.util.TreeResultPrinter;
import iitb.banks.util.TreeScorer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.ArrayUtils;


import com.google.common.primitives.Doubles;
/**
 * Searches and clusters keyword search results.
 * 
 * @author Madhulika Mohanty(madhulikam@cse.iitd.ac.in)
 *
 */
public class SearchAndCluster {
	public static TreeResultPrinter rprint;
	public static SearchParam param = null;
	public static String lastQuery="";
	public static int max_trees=25;
	
	WriteResponse wr;
	PrintWriter pw;
	int rankScheme;
	int clusterId;
	HttpServletRequest req;
	String contextRealPath;
	
	public SearchAndCluster(HttpServletRequest req,PrintWriter pw){
		this.req = req;
		this.pw = pw;
		contextRealPath = req.getSession().getServletContext().getRealPath("/");
		Config.servletContext = contextRealPath;
		rankScheme = Integer.parseInt(req.getParameter("ranking"));
		clusterId = Integer.parseInt(req.getParameter("cluster"));
		wr = new WriteResponse(pw);
	}
	
	public void processBrowserQuery(String QUERY) throws IOException {
		
		int headDisplaycounter=0;
		DataSource ds = doSearchAndCluster(QUERY, headDisplaycounter, wr);

		try{


			// Rank the clusters using the ranking scheme.
			Set<Set<Tree>> rankedClusters = iitb.banks.util.Cluster.clusterRank(iitb.banks.datasource.Graph.clkClustering, rankScheme);  
			writeNDCG(QUERY);

			// write max and min pairs
			genMaxMinPairs(QUERY, rankedClusters);

			//write all functions in javascript
			writeDisplayFunctions(pw,QUERY,rankedClusters.size());

			//Start the left division
			pw.println("<div style=\"overflow: auto;width: 50%; height:100%;float:left\">");



			//initialise required number of treeClusters
			TreeCluster treeClusters[]= getTreeClustersParams(rankedClusters);



			lastQuery= param.queryString;

			String[] entity_terms=ds.getAllEntityTerms();
			setTopEntityTerms(treeClusters, entity_terms);

			String[] relation_terms=ds.getAllRelationTerms();
			setTopRelationTerms(treeClusters,relation_terms);


			printClusterRep(QUERY,rankedClusters, treeClusters);

			//End left division.
			pw.println("</div>");




			//sort everything inside each set and display the cluster-wise results
			printClusters(QUERY, rankedClusters);




			rprint.close();

			param.log.close();
			//if (!doneRes && prevResultInfo != null && param.resFile != null) {
			//param.resFile.println(prevResultInfo);
			param.resFile.close();
			//}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

			if (ds != null)
				wr.writeCompleteFooter();

			if (param != null && param.resFile != null)
				param.resFile.close();
		}
	}

	public void processFile(String qFilename) throws IOException {


		FileInputStream fstream = new FileInputStream(qFilename);
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		int headDisplaycounter=0;
		String brLine = br.readLine();
		while(true){
			if(brLine == null) {
				break;
			}
			String QUERY =brLine;

			DataSource ds = doSearchAndCluster(QUERY, headDisplaycounter, wr);
			headDisplaycounter++;

			try{


				// Rank the clusters using the ranking scheme.
				Set<Set<Tree>> rankedClusters = iitb.banks.util.Cluster.clusterRank(iitb.banks.datasource.Graph.clkClustering, rankScheme);  
				//newSet.addAll(iitb.banks.datasource.Graph.clkClustering); 
				writeNDCG(QUERY);

				// write max and min pairs
				genMaxMinPairs(QUERY, rankedClusters);

				//write all functions in javascript
				writeDisplayFunctions(pw,QUERY,rankedClusters.size());

				//Start the left division
				pw.println("<div style=\"overflow: auto;width: 50%; height:100%;float:left\">");



				//initialise required number of treeClusters
				TreeCluster treeClusters[]= getTreeClustersParams(rankedClusters);




				lastQuery= param.queryString;

				String[] entity_terms=ds.getAllEntityTerms();
				setTopEntityTerms(treeClusters, entity_terms);

				String[] relation_terms=ds.getAllRelationTerms();
				setTopRelationTerms(treeClusters,relation_terms);


				printClusterRep(QUERY,rankedClusters, treeClusters);

				//End left division.
				pw.println("</div>");




				//sort everything inside each set and display the cluster-wise results
				printClusters(QUERY, rankedClusters);


				rprint.close();

				param.log.close();
				//if (!doneRes && prevResultInfo != null && param.resFile != null) {
				//param.resFile.println(prevResultInfo);
				param.resFile.close();
				//}

			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("An exception in file query.");
			} finally {
				brLine=br.readLine();
				if (ds != null && brLine==null)
					wr.writeCompleteFooter();

				if (param != null && param.resFile != null)
					param.resFile.close();
			}
			System.out.println("Done : " + QUERY+" !!!");
		} // end WHILE LOOP
		br.close();
	}

	private void printClusters(String QUERY, Set<Set<Tree>> rankedClusters) throws IOException {
		Iterator<Set<Tree>> iter1 = rankedClusters.iterator();
		int clusterCount = 1;

		while (iter1.hasNext()) {		

			pw.println("<div style=\"overflow: auto;display:none;width: 49%; height:100%; float:right\" id=\""+QUERY.replace(' ', '_')+"_Cluster"+clusterCount+"\">");

			String jsonFile = "http://"+Config.host+Config.URL+"MyAns.html?answerFile="+Config.URL+Config.statsDirectory+Config.LMclusterFiles+
					QUERY.replace(" ", "_").replace("'", "")+"_"+clusterCount+".json";
			pw.println("<iframe width=\"100%\" height=\"100%\" src="+jsonFile + "></iframe>");

			Set<Tree> TreeSet  = (Set<Tree>) iter1.next();

			List<Tree> sortedList = asSortedList(TreeSet);
			try{
				Tree firstTree = (Tree)sortedList.get(0);

				String representativeFile=Config.statsDirectory + "/"+Config.repDirectory+QUERY.replace(" ", "_").replace("'", "")+
						"_rep"+clusterCount+".json";
				File rf = new File(representativeFile);
				if(rf.exists()){
					rf.delete();
				}
				rf.createNewFile();

				FileEdit.writeToFile(representativeFile, "[\n");
				rprint.printRepTree(firstTree.root, representativeFile, param);
				FileEdit.eraseLast(representativeFile);
				FileEdit.writeToFile(representativeFile, "]");

				File repff = new File(this.contextRealPath+"/"+Config.statsDirectory+Config.repDirectory+QUERY.replace(" ", "_").replace("'", "")+
						"_rep"+clusterCount+".json");
				if(repff.exists()){
					repff.delete();
				}
				Files.copy(rf.toPath(), repff.toPath());
			}catch(Exception e){
				e.printStackTrace();
			}
			int len = sortedList.size();
			String filename1 = Config.statsDirectory + Config.LMclusterFiles + QUERY.replace(" ", "_").replace("'", "")+
					"_"+clusterCount+".json";
			File f = new File(filename1);
			if(f.exists()){
				f.delete();
			}
			f.createNewFile();

			FileEdit.writeToFile(filename1, "[\n");

			for (int i = 0; i < len; i++) {
				Tree tree = sortedList.get(i);
				tree.cluster = clusterCount;
				rprint.printTree(tree.root, QUERY, 1, filename1, param);
			}

			FileEdit.eraseLast(filename1);
			FileEdit.writeToFile(filename1, "]");

			System.out.println("FILENAME : " + jsonFile);
			File ff = new File(this.contextRealPath+"/" +Config.statsDirectory + Config.LMclusterFiles+
					QUERY.replace(" ", "_").replace("'", "") +
					"_" +(clusterCount) +".json");
			if(ff.exists()){
				ff.delete();
			}
			Files.copy(f.toPath(), ff.toPath());
			clusterCount++;

		}

		pw.println("</div>");

	}

	private void printClusterRep(String QUERY, Set<Set<Tree>> rankedClusters, TreeCluster[] treeClusters) throws IOException {
		Iterator<Set<Tree>> iter1 = rankedClusters.iterator();
		int clusterCount = 1;

		String clusterDetailsFile = Config.statsDirectory + "/LM_clusterDetails/"+QUERY.replace(" ", "_").
				replace("'", "") + ".txt";
		File cdf = new File(clusterDetailsFile);
		if(cdf.exists()){
			cdf.delete();
		}
		cdf.createNewFile();

		while (iter1.hasNext()) {
			Set<Tree> TreeSet = (Set<Tree>) iter1.next();

			List<Tree> sortedList = asSortedList(TreeSet);

			Tree tree = sortedList.get(0);
			tree.cluster = clusterCount;
			pw.println("<br/><br/><table width=98% cellspacing=0 cellpadding=0 style=\"font-size: 10pt; background-color:#eeeeee; border: 1px solid #808080; position: relative; z-index:10\">");
			/*pw.println("<tr><td><b>Rank:</b> " + tree.rank
					+ "</td>");
			pw.println("<td width=20>&nbsp;</td>");
			pw.println("<td><b>Score:</b> " + tree.totalScore);
			pw.println(" (es=" + tree.edgeScore);
			pw.println(", ns=" + tree.nodeScore + ")");
			pw.println("</td>");
			pw.println("<td width=20>&nbsp;</td>");
			pw.println("<td><b>Seqnum:</b> " + tree.seqnum
					+ "</td>");
			pw.println("<td width=20>&nbsp;</td>");
			pw.println("<td><b>Cluster:</b> " + tree.cluster
					+ "</td>");

			pw.println("</td></tr>");*/
			pw.println("<tr><td><b>Cluster:</b> " + tree.cluster
					+ "</td></tr>");
			pw.println("</table></br></br>");

			//rprint.printTree(tree.root, 1); This one prints the original BANKS version trees.
			pw.println("<div style=\"overflow: auto;display:block;width: 100%; height:30%\" id=\""+QUERY.replace(' ', '_')+"_ClusterRep"+clusterCount+"\">");
			String jsonRepFile = "http://"+Config.host+Config.URL+"MyAns.html?answerFile="+Config.URL+Config.statsDirectory+Config.repDirectory+
					QUERY.replace(" ", "_").replace("'", "")+"_rep"+clusterCount+".json";
			pw.println("<iframe width=\"100%\" height=\"100%\" src="+jsonRepFile + "></iframe>");
			pw.println("</div>");

			pw.println("<br />");


			String button_name=QUERY.replace(' ', '_')+"_Button_Cluster"+clusterCount;
			String function_name=QUERY.replace(' ', '_')+"_show_Cluster"+clusterCount+"()";
			pw.println("<table align=\"left\" style=\"font-size:11pt\"><tr><td><a id=\""+ 
					button_name+"\" onclick=\""+function_name+"; return false;\" href=\"#\">Show Cluster</a></td>");
			pw.println("<td><a href=\"#\" class=\"info\">Cluster Details");
			pw.println("<span class=\"ttip\">");
			pw.println("<table class=\"box-table-b\">");
			pw.println("<tr>");
			//						pw.println("<td>");						
			pw.println("<th width=60%>Entity Terms</th>");
			pw.println("<th width=40%>Relation Terms</th>");
			pw.println("</tr>");

			for(int u=0;u<treeClusters[clusterCount].topEntityTerms.length;u++)
			{
				FileEdit.writeToFile(clusterDetailsFile, treeClusters[clusterCount].topEntityTerms[u]+"DELIMITER");
				pw.println("<tr>   <td width=60%>"+treeClusters[clusterCount].topEntityTerms[u]+"</td>");
			}
			FileEdit.writeToFile(clusterDetailsFile, "\n");
			for(int u=0;u<treeClusters[clusterCount].topEntityTerms.length;u++)
			{
				FileEdit.writeToFile(clusterDetailsFile, treeClusters[clusterCount].topRelationTerms[u]+"DELIMITER");
				pw.println("  <td width=40%>"+treeClusters[clusterCount].topRelationTerms[u]+"</td></tr>");
			}
			FileEdit.writeToFile(clusterDetailsFile, "Cluster-DELIMITER");


			pw.println("</table>");
			pw.println("</span>");
			pw.println("</a></td> </tr></table>");

			pw.println("<br />");

			pw.flush();
			clusterCount++;

		}
		File cdf1 = new File(this.contextRealPath+"/"+Config.statsDirectory+Config.LMclusterFiles +
				QUERY.replace(" ", "_").replace("'", "")+".txt");
		if(cdf1.exists()){
			cdf1.delete();
		}
		Files.copy(cdf.toPath(), cdf1.toPath());
	}

	private void setTopRelationTerms(TreeCluster[] treeClusters, String[] relation_terms) {
		for(int i=1;i<treeClusters.length;i++)
		{
			//the mix array that contains top values against all clusters
			double[] mix_array=new double[2*TreeCluster.topRelationK*(treeClusters.length-2)];
			List<Double> mix_array1=new ArrayList<Double>();
			String[] mix_array_terms=new String[2*TreeCluster.topRelationK*(treeClusters.length-2)];

			int mix_array_cnt=0;

			for(int j=1;j<treeClusters.length;j++)
			{
				if(i!=j)
				{
					System.out.println("\n\nRelations : Cluster "+i+":Cluster "+j);
					double[] array1=Doubles.toArray(treeClusters[i].allRelationValues);
					double[] array2=Doubles.toArray(treeClusters[j].allRelationValues);
					double[] array3=new double[array1.length];
					ArrayList<Double> array4=new ArrayList<Double>();

					for(int t=0;t<array1.length;t++)
					{
						array3[t]=array1[t]-array2[t];
						array4.add(t, array3[t]);
					}

					Arrays.sort(array3);

					for(int k=0,v=0;v<2*TreeCluster.topRelationK;k++)
					{
						int index = array4.indexOf(array3[array3.length-k-1]);
						System.out.println(array3[array3.length-k-1]+":"+relation_terms[index]);
						mix_array[mix_array_cnt]=array3[array3.length-k-1];
						mix_array_terms[mix_array_cnt]=relation_terms[index];
						mix_array_cnt++;
						v++;
						array4.set(index, -1.0);
					}
					array4.clear();
				}
			}

			//copy mix_array to mix_array1
			mix_array1=Arrays.asList(ArrayUtils.toObject(Arrays.copyOf(mix_array, mix_array_cnt)));

			Arrays.sort(mix_array);
			int cnt=0, arrayCounter=mix_array.length-1;

			System.out.println("\n\nFor Cluster "+i);

			while(cnt<TreeCluster.topRelationK&&arrayCounter >=0)
			{
				//Add an element to topk list only if it is not there
				int index=mix_array1.indexOf(mix_array[arrayCounter]);
				if(!ArrayUtils.contains(treeClusters[i].topRelationTerms, mix_array_terms[index]))
				{
					treeClusters[i].topRelationTerms[cnt]= mix_array_terms[index];
					System.out.println(mix_array[arrayCounter]+":"+mix_array_terms[index]);			

					cnt++;
				}
				mix_array1.set(index, 1.0);
				arrayCounter--;
			}

		}

	}

	private void setTopEntityTerms(TreeCluster[] treeClusters, String[] entity_terms) {


		for(int i=1;i<treeClusters.length;i++)
		{
			//the mix array that contains top values against all clusters
			double[] mix_array=new double[TreeCluster.topEntityK*(treeClusters.length-1)];
			List<Double> mix_array1=new ArrayList<Double>();
			String[] mix_array_terms=new String[TreeCluster.topEntityK*(treeClusters.length-1)];

			int mix_array_cnt=0;

			for(int j=1;j<treeClusters.length;j++)
			{
				System.out.println("\n\nCluster "+i+":Cluster "+j);
				double[] array1=Doubles.toArray(treeClusters[i].allEntityValues);
				double[] array2=Doubles.toArray(treeClusters[j].allEntityValues);
				double[] array3=new double[array1.length];
				ArrayList<Double> array4=new ArrayList<Double>();

				for(int t=0;t<array1.length;t++)
				{
					array3[t]=array1[t]-array2[t];
					array4.add(t, array3[t]);
				}

				//sort the array in descending order
				Arrays.sort(array3);

				//fetch top k values starting from end
				for(int k=0;k<TreeCluster.topEntityK;k++)
				{

					int index = array4.indexOf(array3[array3.length-k-1]);
					System.out.println(array3[array3.length-k-1]+":"+entity_terms[index]);
					mix_array[mix_array_cnt]=array3[array3.length-k-1];
					mix_array_terms[mix_array_cnt]=entity_terms[index];
					mix_array_cnt++;
					array4.set(index, -1.0);
				}
				array4.clear();
			}

			//copy mix_array to mix_array1
			mix_array1=Arrays.asList(ArrayUtils.toObject(Arrays.copyOf(mix_array, mix_array_cnt)));

			//now sort the mixture array and fetch top 10
			Arrays.sort(mix_array);
			int cnt=0, arrayCounter=mix_array.length-1;

			System.out.println("\n\nFor Cluster "+i);

			while(cnt<TreeCluster.topEntityK&&arrayCounter >=0)
			{
				//Add an element to topk list only if it is not there
				int index=mix_array1.indexOf(mix_array[arrayCounter]);
				if(!ArrayUtils.contains(treeClusters[i].topEntityTerms, mix_array_terms[index]))
				{
					treeClusters[i].topEntityTerms[cnt]= mix_array_terms[index];
					System.out.println(mix_array[arrayCounter]+":"+mix_array_terms[index]);			

					cnt++;
				}
				mix_array1.set(index, -1.0);
				arrayCounter--;
			}
		}
	}

	private TreeCluster[] getTreeClustersParams(Set<Set<Tree>> rankedClusters) {
		// sort everything inside each set and display the cluster-wise results
		Iterator<Set<Tree>> iter1 = rankedClusters.iterator();
		int clusterCount = 1;
		TreeCluster treeClusters[]= new TreeCluster[rankedClusters.size()+1];

		while (iter1.hasNext()) {		

			treeClusters[clusterCount]=new TreeCluster();

			Set<Tree> TreeSet = (Set<Tree>) iter1.next();

			List<Tree> sortedList = asSortedList(TreeSet);
			int len = sortedList.size();

			for (int i = 0; i < len; i++) {
				Tree tree = sortedList.get(i);

				//add tree values to the allValues of current cluster
				double[] array1=Doubles.toArray(tree.vectorEntityTerms);
				double[] arrayr1=Doubles.toArray(tree.vectorRelationTerms);

				if(treeClusters[clusterCount].allEntityValues.isEmpty()) 
				{ 
					for(int m=0;m<array1.length;m++)
					{
						treeClusters[clusterCount].allEntityValues.add(m,0.0); 
					} 
				} 
				if(treeClusters[clusterCount].allRelationValues.isEmpty()) 
				{ 
					for(int m=0;m<arrayr1.length;m++)
					{
						treeClusters[clusterCount].allRelationValues.add(m,0.0); 
					} 
				} 
				for(int m=0;m<array1.length;m++)
				{
					Double d=treeClusters[clusterCount].allEntityValues.get(m);
					treeClusters[clusterCount].allEntityValues.set(m, d+array1[m]);
				}
				for(int m=0;m<arrayr1.length;m++)
				{
					Double d=treeClusters[clusterCount].allRelationValues.get(m);
					treeClusters[clusterCount].allRelationValues.set(m, d+arrayr1[m]);
				}
				//if (clusterId <= 0)
				tree.cluster = clusterCount;

			}

			//take the average of all values for the current cluster
			for(int l=0;l<treeClusters[clusterCount].allEntityValues.size();l++)
			{
				treeClusters[clusterCount].allEntityValues.set(l, treeClusters[clusterCount].allEntityValues.get(l)/len);
			}
			for(int l=0;l<treeClusters[clusterCount].allRelationValues.size();l++)
			{
				treeClusters[clusterCount].allRelationValues.set(l, treeClusters[clusterCount].allRelationValues.get(l)/len);
			}
			pw.flush();
			clusterCount++;

		}
		return treeClusters;
	}

	private DataSource doSearchAndCluster( String query, int headDisplayCounter, WriteResponse wr) throws IOException {
		String prevResultInfo = null;
		boolean doneRes = false;
		double free_mem = 0D;
		int count = 0;
		BackwardExpandingRI bad_ri = null;
		Mult_BackwardExpandingRI mult_bad_ri = null;

		System.out.println("I am in SearchAndCluster");
		DataSource ds = null;
		TreeScorer ts;
		ResultIterator ri = null;
		long dbLoadt = 0, dbLookupt = 0;

		
		long startt, endt;
		System.out.println("==========" + iitb.banks.util.Config.URL
				+ "===========");
		boolean wait = false;
		try {
			startt = System.currentTimeMillis();
			param = new SearchParam(req, query);//No need to pass 'k', line[1]);
			param.pw = null;// new PrintWriter(new
			param.log = new Logger();
			wait = false;
			ds = DataSource.getInstanceWait(param.dsidx, wait);

			if (ds == null) {
				wr.printWaitScreen(req.getQueryString());
				return ds;
			}

			if (param.queryTree != null) {
				System.out.print(param.queryTree);
			} else {
				System.out.println("Not a path query");
			}
			dbLoadt = System.currentTimeMillis() - startt;
			ts = createTreeScorer(ds, req, param.treescorer, param);
			String q = ds.findKeywordNodes(param, ts);
			dbLookupt = System.currentTimeMillis() - startt - dbLoadt;

			Runtime rt = Runtime.getRuntime();

			if (clusterId <= 0) {

				if(lastQuery.equals(param.queryString)==false)
				{				

					if (iitb.banks.datasource.Graph.clkClustering != null)
						iitb.banks.datasource.Graph.clkClustering.clear();
					if(iitb.banks.datasource.Graph.ResultTrees!=null)
						iitb.banks.datasource.Graph.ResultTrees.clear();
					System.out.println("Clusters Are : "+iitb.banks.datasource.Graph.clkClustering);

					bad_ri = null;
					mult_bad_ri = null;

					System.gc();
					free_mem = rt.freeMemory();
					System.out.println("[MEMORY] Start loading: Total= "
							+ (rt.totalMemory() / 1000000) + "M Free= "
							+ (rt.freeMemory() / 1000000) + "M Used= "
							+ ((rt.totalMemory() - rt.freeMemory()) / 1000000)
							+ "M");

					switch (param.algorithm) {
					case SearchParam.ALG_BACKWARD:
						bad_ri = new BackwardExpandingRI(ds, ts, param);
						ri = new BufferedRI(bad_ri, ts, param,ds);
						break;
					case SearchParam.ALG_BACKWARD_MULT:
						mult_bad_ri = new Mult_BackwardExpandingRI(ds, ts, param);
						ri = new BufferedRI(mult_bad_ri, ts, param,ds);
						break;
					case SearchParam.ALG_BIDIREC:
						if (param.queryTree == null)
							ri = new BidirectionalRI(ds, ts, param);
						else
							ri = new TwigRI(ds, ts, param);
						break;
					case SearchParam.ALG_RANDWALK:
						ri = new RandomWalkRI(ds, ts, param);
						break;
					default:
						ri = new BidirectionalRI(ds, ts, param);
						break;
					}

				}// end of lastQuery == queryString

			}// end of if clusterid<=0

			// Write the initial header only once.
			if(headDisplayCounter==0){
				wr.writeSearchHeader(param, param.dsidx);
			}
			else{
				wr.writeModifiedHeader(param, param.dsidx);
			}

			// Initialize result printer.
			if (param.resultPrinter == SearchParam.RP_TREE)
				rprint = new TreeResultPrinter(pw, ds, param.dsidx, param);
			else if (param.resultPrinter == SearchParam.RP_PRETTYTREE)
				rprint = new PrettyResultPrinter(pw, ds, param.dsidx, param);
			else
				rprint = null;

			if (param.firstResult == 0)
				rprint.clearCache();

			if (clusterId <= 0) {
				if(lastQuery.equals(param.queryString)==false)
				{
					count = ri.getPosition();
					int prevTreeSize = -1;
					doneRes = false;
					prevResultInfo = null;
					System.out.println("[MEMORY] Cleaned garbage : Total= "
							+ (rt.totalMemory() / 1000) + "K Free= "
							+ (rt.freeMemory() / 1000) + "K Used= "
							+ ((rt.totalMemory() - rt.freeMemory()) / 1000) + "K");


					while (ri.hasNext() && count < max_trees) {
						Tree tree = ri.next();

						iitb.banks.datasource.Graph.ResultTrees.add(tree);

						//compute all entities and relations in the current tree
						tree.addEntitiesRelations(ds,tree.root,null);
						tree.computeEntityVector();
						tree.computeRelationVector();

						System.out.println("[MEMORY] At res count " + count
								+ " : Total= " + (rt.totalMemory() / 1000)
								+ "K Free= " + (rt.freeMemory() / 1000)
								+ "K Used= "
								+ ((free_mem - rt.freeMemory()) / 1000) + "K");

						if (count < param.firstResult) {
							System.out.println("skipping over ri.position="
									+ ri.getPosition());
							count++;
							continue;
						}
						tree.rank = count + 1;

						int nodes_exp1, nodes_touched1;
						if (param.algorithm == SearchParam.ALG_BACKWARD) {
							nodes_exp1 = bad_ri.nc;
							nodes_touched1 = bad_ri.getNodesTouched();
						} else if (param.algorithm == SearchParam.ALG_BACKWARD_MULT) {
							nodes_exp1 = mult_bad_ri.nc;
							nodes_touched1 = mult_bad_ri.getNodesTouched();
						} else {

							nodes_exp1 = ri.nc;
							nodes_touched1 = ri.getNodesTouched();
						}


						long time = (System.currentTimeMillis() - startt) - dbLoadt
								- dbLookupt;
						System.out.println("ResNo: " + count + " Time: " + time
								+ " Nodes Explored: " + nodes_exp1
								+ " Nodes touched: " + nodes_touched1);


						if (prevTreeSize == -1)
							prevTreeSize = tree.treeSize;
						else if (!doneRes) {
							if (tree.treeSize > 1 && tree.treeSize > prevTreeSize
									&& param.resFile != null
									&& prevResultInfo != null) {// write res now
								param.resFile.println(prevResultInfo);
								doneRes = true;
							}

						}
						prevResultInfo = "" + param.algorithm + ":" + (count + 1)
								+ ":" + tree.treeSize + ":" + nodes_exp1 + ":"
								+ nodes_touched1 + ":" + time + ":" + param.query;

						System.out.println("Result " + count + "> "
								+ tree.totalScore + " - " + tree.fwd + " size: "
								+ tree.treeSize);
						count++;

					}
					endt = System.currentTimeMillis();
					wr.writeSearchParam(param, ds.getDescription(),
							param.firstResult, endt - startt);
					pw.println("<br/><br/><div class=qinfo>"
							+ q
							+ " <i>Click on keywords to select or filter nodes.</i> Time Profile: "
							+ dbLoadt + ":" + dbLookupt + ":"
							+ (endt - startt - dbLoadt - dbLookupt)
							+ "[dbLoad:dbLookup:Expansion]</div>");
					Tree.vectorCache.clear();
				}

				// Cluster results
				iitb.banks.datasource.Graph.clkClustering =iitb.banks.util.Cluster.cluster(iitb.banks.datasource.Graph.ResultTrees);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Exception caught in doSearchAndCluster()!");
		}
		return ds;
	}

	private void genMaxMinPairs(String query, Set<Set<Tree>> rankedClusters) {
		//Writing the Max and Min pairs files.
		Iterator<Set<Tree>>  iter1=rankedClusters.iterator();
		int clustCount=1;
		while(iter1.hasNext()){
			Set<Tree> TreeSet = (Set<Tree>) iter1.next();
			ArrayList<Tree> allTrees = new ArrayList<Tree>();
			Iterator<Tree> trIter=TreeSet.iterator();
			Set<Tree> minSet=new HashSet<Tree>();
			Set<Tree> maxSet=new HashSet<Tree>();
			while(trIter.hasNext()){
				allTrees.add(trIter.next());
			}
			if(allTrees.size()==1){
				minSet.add(allTrees.get(0));
				maxSet.add(allTrees.get(0));
			}
			else{
				double minVal=2.0;
				double maxVal=-1.0;
				for(int i=0;i<allTrees.size();i++){
					Tree tI=allTrees.get(i);
					for(int k=0;k<allTrees.size();k++){
						Tree tK=allTrees.get(k);
						if(i==k)
							continue;	
						double dVal=0.0;
						//System.out.println("Calculating distance.");
						dVal=Cluster.distanceBetweenTrees(tI, tK);
						//System.out.println("Done calculation of distance.");
						if(dVal>maxVal){
							maxSet=new HashSet<Tree>();
							maxVal=dVal;
							maxSet.add(tI);
							maxSet.add(tK);
						}
						if(dVal<minVal){
							minSet=new HashSet<Tree>();
							minVal=dVal;
							minSet.add(tI);
							minSet.add(tK);
						}
					}
				}
			}
			writeMaxMinPairs(maxSet, query, clustCount, true);
			writeMaxMinPairs(minSet, query, clustCount, false);
			clustCount++;
		}
		//Writing the Max and Min pairs files ENDS.
	}

	private void writeMaxMinPairs(Set<Tree> set, String query, int clustCount, boolean isMax) {
		try{
			String file1, file2;
			if(isMax){
				file1=Config.statsDirectory + "/"+Config.maxMinDirectory+query.replace(" ", "_").replace("'", "")+
						"_max"+clustCount+"-1.json";
				file2=Config.statsDirectory + "/"+Config.maxMinDirectory+query.replace(" ", "_").replace("'", "")+
						"_max"+clustCount+"-2.json";
			}
			else{
				file1=Config.statsDirectory + "/"+Config.maxMinDirectory+query.replace(" ", "_").replace("'", "")+
						"_min"+clustCount+"-1.json";
				file2=Config.statsDirectory + "/"+Config.maxMinDirectory+query.replace(" ", "_").replace("'", "")+
						"_min"+clustCount+"-2.json";
			}

			//Writing the pairs.
			Iterator<Tree> setIter=set.iterator();
			int m_i=1;
			while(setIter.hasNext()){
				Tree mtmp=setIter.next();
				String writeFile = "";
				if(m_i==1)
					writeFile=file1;
				else
					writeFile=file2;

				File rf = new File(writeFile);
				if(rf.exists()){
					rf.delete();
				}
				rf.createNewFile();

				FileEdit.writeToFile(writeFile, "[\n");
				rprint.printRepTree(mtmp.root, writeFile, param);
				FileEdit.eraseLast(writeFile);
				FileEdit.writeToFile(writeFile, "]");
				String suffix;
				if(isMax)
					suffix = "max";
				else
					suffix = "min";
				File repff = new File(this.contextRealPath+"/"+Config.statsDirectory+Config.maxMinDirectory+query.replace(" ", "_").replace("'", "")+
						"_"+suffix+clustCount+"-"+m_i+".json");
				if(repff.exists()){
					repff.delete();
				}
				Files.copy(rf.toPath(), repff.toPath());
				m_i++;
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	private void writeNDCG(String QUERY) throws IOException {
		//Begin of NDCG order writing.

		// Get "highest" based ordering.
		Set<Set<Tree>> rankedClusters0 = iitb.banks.util.Cluster.clusterRank(iitb.banks.datasource.Graph.clkClustering, 0);
		// Write Lowest.
		String rankVals1 = computeNDCG(rankedClusters0, 1);
		// Write Average.
		String rankVals2 = computeNDCG(rankedClusters0, 2);
		// Write Largest
		String rankVals3 = computeNDCG(rankedClusters0, 3);

		System.out.println("RanksVals1:"+rankVals1);
		System.out.println("RanksVals2:"+rankVals2);
		System.out.println("RanksVals3:"+rankVals3);
		File file=new File(Config.servletContext+Config.statsDirectory + Config.NDCGFile);

		if (!file.exists()) {
			file.createNewFile();
		}
		FileWriter fw = new FileWriter( file.getAbsoluteFile( ),true );
		BufferedWriter bw = new BufferedWriter( fw );
		bw.write( QUERY+"," );
		//bw.newLine();
		bw.write(rankVals1+rankVals2+rankVals3);
		bw.newLine();
		bw.close();
		//End of NDCG order writing.
	}

	private String computeNDCG(Set<Set<Tree>> rankedClusters0, int rankNum) {
		Set<Set<Tree>> rankedClusters_n = iitb.banks.util.Cluster.clusterRank(iitb.banks.datasource.Graph.clkClustering, rankNum);

		String rankVals = "";
		switch(rankNum){
		case 1:
			rankVals="Lowest";
			break;
		case 2: 
			rankVals=",Average";
			break;
		case 3:
			rankVals=",Largest";
			break;
		}

		Iterator<Set<Tree>>  iterRank=rankedClusters_n.iterator();
		while(iterRank.hasNext()){
			Set<Tree> rank1treeSet=iterRank.next();
			Iterator<Tree> trIter=rank1treeSet.iterator();
			Tree rank1tree=trIter.next();
			Iterator<Set<Tree>>  iterSet=rankedClusters0.iterator();
			int rank=1;
			while(iterSet.hasNext()){
				Set<Tree> highTreeSet=iterSet.next();
				Iterator<Tree> trIter1=highTreeSet.iterator();
				Tree firstTree=trIter1.next();
				if(firstTree.compareTo(rank1tree)==0){
					rankVals=rankVals+","+rank;
					break;
				}
				rank++;
			}
		}
		return rankVals;
	}

	private void writeDisplayFunctions(PrintWriter pw, String QUERY, int size) {
		pw.println("<script type=\"text/javascript\">");

		for(int i=1;i<=size;i++)
		{
			String function_name = QUERY.replace(' ', '_')+"_show_Cluster"+i+"()";
			String button_name = QUERY.replace(' ', '_')+"_Button_Cluster"+i;
			String cluster_name=QUERY.replace(' ', '_')+"_Cluster"+i;

			pw.println("function "+ function_name+"{");
			pw.println("if(document.getElementById('"+button_name+"').innerHTML=='Hide Cluster')");
			pw.println("{");
			pw.println("document.getElementById('"+button_name+"').innerHTML = 'Show Cluster'");
			pw.println("document.getElementById('"+cluster_name+"').style.display=\"none\"");
			pw.println("} else");

			pw.println("{");
			for(int j=1;j<=size;j++)
			{
				if(j==i)
				{
					pw.println("document.getElementById('"+button_name+"').innerHTML = 'Hide Cluster'");
					pw.println("document.getElementById('"+ cluster_name+"').style.display=\"block\"");
				}
				else
				{
					String button_name2=QUERY.replace(' ', '_')+"_Button_Cluster"+j;
					String cluster_name2=QUERY.replace(' ', '_')+"_Cluster"+j;	

					pw.println("document.getElementById('"+button_name2+"').innerHTML = 'Show Cluster'");
					pw.println("document.getElementById('"+ cluster_name2+"').style.display=\"none\"");
				}
			}
			pw.println("}}");


		}
		pw.println("</script>");

	}

	private TreeScorer createTreeScorer(DataSource ds, HttpServletRequest req,
			String treescorer, SearchParam sp) {
		int meanType = 0, combType = 0, nsType = 0, esType = 0, esComb = 0, psType = 0;
		float lambda = 0.0f, gamma = 0.0f;
		String par;
		TreeScorer ts;

		par = req.getParameter("meanType");
		if ("arithmetic".equalsIgnoreCase(par))
			meanType = ActivationTreeScorer.MEAN_ARITHMETIC;
		else if ("geometric".equalsIgnoreCase(par))
			meanType = ActivationTreeScorer.MEAN_GEOMETRIC;
		else if ("harmonic".equalsIgnoreCase(par))
			meanType = ActivationTreeScorer.MEAN_HARMONIC;
		else
			// default values
			meanType = ActivationTreeScorer.MEAN_HARMONIC;

		par = req.getParameter("combType");
		if ("arithmetic".equalsIgnoreCase(par))
			combType = TreeScorer.COMB_ARITHMETIC;
		else if ("geometric".equalsIgnoreCase(par))
			combType = TreeScorer.COMB_GEOMETRIC;
		else
			combType = TreeScorer.COMB_GEOMETRIC;

		par = req.getParameter("nsType");
		if ("linear".equalsIgnoreCase(par))
			nsType = TreeScorer.NS_LINEAR;
		else if ("log".equalsIgnoreCase(par))
			nsType = TreeScorer.NS_LOG;
		else
			nsType = TreeScorer.NS_LOG;

		par = req.getParameter("psType");
		if ("linear".equalsIgnoreCase(par))
			psType = TreeScorer.NS_LINEAR;
		else if ("log".equalsIgnoreCase(par))
			psType = TreeScorer.NS_LOG;
		else
			nsType = TreeScorer.NS_LOG;

		par = req.getParameter("esType");
		if ("log".equalsIgnoreCase(par))
			esType = ConventionalTreeScorer.ES_LOG;
		else if ("power".equalsIgnoreCase(par))
			esType = ConventionalTreeScorer.ES_POWER;
		else
			esType = ConventionalTreeScorer.ES_POWER;

		par = req.getParameter("esComb");
		if ("sum".equalsIgnoreCase(par))
			esComb = ConventionalTreeScorer.EC_SUM;
		else if ("prod".equalsIgnoreCase(par))
			esComb = ConventionalTreeScorer.EC_PROD;
		else
			esComb = ConventionalTreeScorer.EC_PROD;

		par = req.getParameter("lambda");
		if (par != null) {
			try {
				lambda = Float.parseFloat(par);
			} catch (NumberFormatException nfe) {
				lambda = 0.2f;
			}
		} else
			lambda = 0.2f;

		par = req.getParameter("gamma");
		if (par != null) {
			try {
				gamma = Float.parseFloat(par);
			} catch (NumberFormatException nfe) {
				gamma = 0.5f;
			}
		} else
			gamma = 0.5f;

		sp.ats = new ActivationTreeScorer(ds, meanType, combType, nsType,
				lambda, psType);

		sp.cts = new ConventionalTreeScorer(ds, combType, nsType, esType,
				esComb, gamma, lambda);

		if ((treescorer != null && treescorer.equalsIgnoreCase("probability"))) {
			ts = new ActivationTreeScorer(ds, meanType, combType, nsType,
					lambda, psType);
		} else {
			ts = new ConventionalTreeScorer(ds, combType, nsType, esType,
					esComb, gamma, lambda);
		}

		return ts;
	}

	public static
	<T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
		List<T> list = new ArrayList<T>(c);
		java.util.Collections.sort(list);
		return list;
	}

	class FirstNameComparator implements Comparator<Set<Tree>> {  

		public int compare(Set<Tree> _firstPerson, Set<Tree> _secondPerson) {  
			List<Tree> list1 = asSortedList(_firstPerson);
			List<Tree> list2 = asSortedList(_secondPerson);

			return(list1.get(0).compareTo(list2.get(0)));			  
		}  

	}  


}
