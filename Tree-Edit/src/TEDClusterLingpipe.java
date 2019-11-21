import java.io.*;
import java.sql.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.file.Files;

import javax.servlet.*;
import javax.servlet.http.*;

import iitb.banks.util.*;
import iitb.banks.util.Config;
import iitb.banks.datasource.*;
import iitb.banks.search.*;

public class TEDClusterLingpipe extends HttpServlet 
{
	public void init(ServletConfig sc) 
			throws ServletException 
	{
		super.init(sc);
		System.out.println("-----------------------------------------------");
		System.out.println(" Super Fast BANKS Search initializing     "); 
		System.out.println("-----------------------------------------------");

	}

	public void destroy()
	{
		try {
			int i=1;
		}
		catch (Exception sqe) {
			System.out.println(sqe);
			sqe.printStackTrace();
		}
	}

	public void doPost(HttpServletRequest req, HttpServletResponse res) 
			throws IOException,ServletException 
	{
		doGet(req,res);
	}

	public void doGet( HttpServletRequest req, HttpServletResponse res) 
			throws IOException,ServletException 
	{
		int chk = 1;
		if(req.getParameter("q_type").equals("browser")){
			System.out.println("YES\n\n");
			boolean Flag_Has_Results = false;
			DataSource ds=null;
			TreeScorer ts;
			ResultIterator ri;
			SearchParam param=null;
			PrintWriter pw = null;
			TreeResultPrinter rprint;
			long dbLoadt=0,dbLookupt=0;
			res.setContentType("text/html");
			pw = res.getWriter();
			WriteResponse wr = new WriteResponse(pw);
			long startt, endt;

			String waitScreen = req.getParameter("wait");

			System.out.println("=========="+iitb.banks.util.Config.URL+"===========");
			String query = req.getParameter("q");
	
			boolean wait = false;
			try {
				startt = System.currentTimeMillis();

				param = new SearchParam(req);

				param.pw =null;
				param.log = new Logger();

				wait = waitScreen.equals("true");
				ds = DataSource.getInstanceWait(param.dsidx,wait);
				if (ds==null) {
					wr.printWaitScreen(req.getQueryString(),getClass().getName());
					return;
				}
				if (param.queryTree!=null) {
					System.out.print(param.queryTree);
				}else {
					System.out.println("Not a path query");
				}
				dbLoadt=System.currentTimeMillis()-startt;
				ts = createTreeScorer(ds, req, param.treescorer,param);
				String q = ds.findKeywordNodes(param, ts);
				dbLookupt=System.currentTimeMillis()-startt-dbLoadt;

				Runtime rt = Runtime.getRuntime();
				BackwardExpandingRI bad_ri=null; 
				Mult_BackwardExpandingRI mult_bad_ri=null; 
				System.gc();
				double free_mem=rt.freeMemory();
				System.out.println("[MEMORY] Start loading: Total= " + 
						(rt.totalMemory()/1000000)+ "M Free= " + 
						(rt.freeMemory()/1000000) + "M Used= " +  
						((rt.totalMemory() - rt.freeMemory())/1000000)+"M");


				switch (param.algorithm) {
				case SearchParam.ALG_BACKWARD:
					bad_ri = new BackwardExpandingRI(ds, ts, param);
					ri = new BufferedRI(bad_ri, ts, param);  
					break;
				case SearchParam.ALG_BACKWARD_MULT:
					mult_bad_ri = new Mult_BackwardExpandingRI(ds, ts, param);
					ri = new BufferedRI(mult_bad_ri, ts, param);  
					break;
				case SearchParam.ALG_BIDIREC:
					if (param.queryTree==null)
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



				if (param.resultPrinter == SearchParam.RP_TREE)
					rprint = new TreeResultPrinter(pw, ds,param.dsidx,param);
				else if (param.resultPrinter == SearchParam.RP_PRETTYTREE)
					rprint = new PrettyResultPrinter(pw, ds,param.dsidx,param);
				else
					rprint = null;

				if(param.firstResult == 0)
					rprint.clearCache();
				wr.writeSearchHeader(param, param.dsidx);
				int count = ri.getPosition();
				int prevTreeSize=-1;
				boolean doneRes=false;
				String prevResultInfo=null;
				ArrayList<String> allTrees = new ArrayList<String>();
				ArrayList<Tree> Trees = new ArrayList<Tree>();
				int i =0;
				while (ri.hasNext() && i < iitb.banks.util.Config.maxRslts){
					Trees.add(ri.next());
					allTrees.add(getTree(Trees.get(i).root,"Semantic", param, ds));
					i++;
				}
				double[][]editSimilarity=util.RTEDCommandLine.generateEditSimilarity(allTrees);
				ArrayList<String> clusters=iitb.banks.util.Cluster.cluster(allTrees,editSimilarity);	

				//Writing the Max and Min pairs files.
				for(int j=0;j<clusters.size();j++){
					String[] treesCluster = clusters.get(j).split("-");
					int index1 = Integer.parseInt(treesCluster[0]);
					System.out.println("Index:"+index1);
					Tree tree1 = Trees.get(index1-1);
					Tree tree2=null;
					if(treesCluster.length>1){
						int index2 = Integer.parseInt(treesCluster[1]);
						tree2 = Trees.get(index2-1);
					}
					Set<Tree> maxSet=new HashSet<Tree>();
					maxSet.add(tree1);
					if(tree2!=null)
						maxSet.add(tree2);
					double minSimilarity=2;
					Set<Tree> minSet=new HashSet<Tree>();
					Tree mintree1=null;
					Tree mintree2=null;
					for(int cntt=0;cntt<treesCluster.length;cntt++){
						int treeindex1 = Integer.parseInt(treesCluster[cntt]);
						for(int cnt=cntt+1;cnt<treesCluster.length;cnt++){
							int treeindex2=Integer.parseInt(treesCluster[cnt]);
							if(minSimilarity>editSimilarity[treeindex1-1][treeindex2-1]){
								minSimilarity=editSimilarity[treeindex1-1][treeindex2-1];
								mintree1=Trees.get(treeindex1-1);
								mintree2=Trees.get(treeindex2-1);
							}
						}
					}
					if (mintree1!=null)
						minSet.add(mintree1);
					if (mintree2!=null)
						minSet.add(mintree2);
					try{

						String minFile1=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+query.replace(" ", "_").replace("'", "")+
								"_min"+(j+1)+"-1.json";
						String maxFile1=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+query.replace(" ", "_").replace("'", "")+
								"_max"+(j+1)+"-1.json";
						String minFile2=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+query.replace(" ", "_").replace("'", "")+
								"_min"+(j+1)+"-2.json";
						String maxFile2=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+query.replace(" ", "_").replace("'", "")+
								"_max"+(j+1)+"-2.json";

						//Writing Min pairs.
						Iterator<Tree> minIter=minSet.iterator();
					int min_i=1;
					while(minIter.hasNext()){
						Tree mtmp=minIter.next();
						String writeFile = "";
						if(min_i==1)
							writeFile=minFile1;
						else
							writeFile=minFile2;

					File minf = new File(writeFile);
					if(minf.exists()){
						minf.delete();
					}
					minf.createNewFile();

					FileEdit.writeToFile(writeFile, "[\n");
					rprint.printRepTree(mtmp.root, writeFile, param);
					FileEdit.eraseLast(writeFile);
					FileEdit.writeToFile(writeFile, "]");
					File minff = new File(getServletContext().getRealPath("/")+"/stats/"+Config.tedmaxMinDirectory+query.replace(" ", "_").replace("'", "")+
							"_min"+(j+1)+"-"+min_i+".json");

					if(minff.exists()){
						minff.delete();
					}
					Files.copy(minf.toPath(), minff.toPath());
					min_i++;
					}






						//Writing MAX pairs
						Iterator<Tree> maxIter=maxSet.iterator();
						int max_i=1;
						while(maxIter.hasNext()){
							Tree mtmp=maxIter.next();
							String writeFile = "";
							if(max_i==1)
								writeFile=maxFile1;
							else
								writeFile=maxFile2;

							File mf = new File(writeFile);
							if(mf.exists()){
								mf.delete();
							}
							mf.createNewFile();

							FileEdit.writeToFile(writeFile, "[\n");
							rprint.printRepTree(mtmp.root, writeFile, param);
							FileEdit.eraseLast(writeFile);
							FileEdit.writeToFile(writeFile, "]");
							File mff = new File(getServletContext().getRealPath("/")+"/stats/"+Config.tedmaxMinDirectory+query.replace(" ", "_").replace("'", "")+
									"_max"+(j+1)+"-"+max_i+".json");

							if(mff.exists()){
								mff.delete();
							}
							Files.copy(mf.toPath(), mff.toPath());
							max_i++;
						}






						



						//Writing Representative file.
						String representativeFile=Config.statsDirectory + "/"+Config.tedrepDirectory+query.replace(" ", "_").replace("'", "")+
								"_rep"+(j+1)+".json";
						File rf = new File(representativeFile);
						if(rf.exists()){
							rf.delete();
						}
						rf.createNewFile();

						FileEdit.writeToFile(representativeFile, "[\n");
						rprint.printRepTree(tree1.root, representativeFile, param);
						FileEdit.eraseLast(representativeFile);
						FileEdit.writeToFile(representativeFile, "]");

						File repff = new File(getServletContext().getRealPath("/")+"/stats/"+Config.tedrepDirectory+query.replace(" ", "_").replace("'", "")+
								"_rep"+(j+1)+".json");
						if(repff.exists()){
							repff.delete();
						}
						Files.copy(rf.toPath(), repff.toPath());

					}catch(Exception e){
						e.printStackTrace();
					}

				}
				//Writing the Max and Min pairs files ENDS.

				pw.println("<script type=\"text/javascript\">");

				for(i=1;i<=clusters.size();i++)
				{
					String function_name = "show_Cluster"+i+"()";
					String button_name = "Button_Cluster"+i;
					String cluster_name="Cluster"+i;

					pw.println("function "+ function_name+"{");
					pw.println("if(document.getElementById('"+button_name+"').innerHTML=='Hide Cluster')");
					pw.println("{");
					pw.println("document.getElementById('"+button_name+"').innerHTML = 'Show Cluster'");
					pw.println("document.getElementById('"+cluster_name+"').style.display=\"none\"");
					pw.println("} else");

					pw.println("{");
					for(int j=1;j<=clusters.size();j++)
					{
						if(j==i)
						{
							pw.println("document.getElementById('"+button_name+"').innerHTML = 'Hide Cluster'");
							pw.println("document.getElementById('"+ cluster_name+"').style.display=\"block\"");
						}
						else
						{
							String button_name2="Button_Cluster"+j;
							String cluster_name2="Cluster"+j;	

							pw.println("document.getElementById('"+button_name2+"').innerHTML = 'Show Cluster'");
							pw.println("document.getElementById('"+ cluster_name2+"').style.display=\"none\"");
						}
					}
					pw.println("}}");
				}
				pw.println("</script>");

				pw.flush();
				pw.println("<div class=qinfo>"
						+ q
						+ " <i>Click on keywords to select or filter nodes.</i></div>");
				pw.println("<div style=\"overflow: auto;width: 50%; height:100%;float:left\">");
				System.out.println("Number of clusters:"+clusters.size());
				for(i =0; i< clusters.size(); i++){
					pw.flush();

					String[] treesInThisCluster = clusters.get(i).split("-");
					System.out.println(clusters.get(i));
					int index = Integer.parseInt(treesInThisCluster[0]);
					Tree tree = Trees.get(index-1);

					pw.println("<br/><br/><table width=98% cellspacing=0 cellpadding=0 style=\"font-size: 10pt; background-color:#eeeeee; border: 1px solid #808080; position: relative; z-index:10\">");
					
					pw.println("<tr><td><b>Cluster:</b> " + (i+1)
							+ "</td></tr>");
					pw.println("</table></br></br>");

					pw.println("<div style=\"overflow: auto;display:block;width: 100%; height:30%\" id=\""+"ClusterRep"+(i+1)+"\">");
					String jsonRepFile = Config.host+"/TreeEdit/MyAns.html?answerFile=TreeEdit/stats/"+Config.tedrepDirectory+
							query.replace(" ", "_").replace("'", "")+"_rep"+(i+1)+".json";
					pw.println("<iframe width=\"100%\" height=\"100%\" src="+jsonRepFile + "></iframe>");
					pw.println("</div>");
					pw.println("<br />");

					String button_name="Button_Cluster"+(i+1);
					String function_name="show_Cluster"+(i+1)+"()";
					pw.println("<table align=\"left\" style=\"font-size:11pt\"><tr><td><a id=\""+ button_name+"\" onclick=\""+function_name+"; return false;\" href=\"#\">Show Cluster</a></td></table>");

					pw.flush();
				}

				pw.println("</div>");
				for(i =0; i< clusters.size(); i++){
					pw.println("<div style=\"overflow: auto;display:none;width: 49%; height:100%; float:right\" id=\"Cluster"+(i+1)+"\">");

					String jsonFile =Config.host+"TreeEdit/MyAns.html?answerFile=TreeEdit/stats/TED_clusterFiles/"+
							query.replace(" ", "_").replace("'", "")+"_"+(i+1)+".json";
					pw.println("<iframe width=\"100%\" height=\"100%\" src="+jsonFile + "></iframe>");

					String filename = getServletContext().getRealPath("/")+"/stats/TED_clusterFiles/"+query.replace(" ", "_").replace("'", "") +
							"_" +(i+1) +".json";
					System.out.println("Writing to file:::::::::::::::::::"+filename);
					File f2 = new File(filename);
					if(f2.exists()){
						f2.delete();
					}
					f2.createNewFile();
					FileEdit.writeToFile(filename, "[\n");
					String[] treesInThisCluster = clusters.get(i).split("-");
					for(int j =0; j< treesInThisCluster.length; j++){

						int	index = Integer.parseInt(treesInThisCluster[j]);
						Tree tree = Trees.get(index-1);
						

						rprint.printTree(tree.root, query, 1,filename, param);
					}

					pw.println("</div>");
					FileEdit.eraseLast(filename);
					FileEdit.writeToFile(filename, "]");

					File f = new File(Config.statsDirectory+"TED_clusterFiles/" +
							query.replace(" ", "_").replace("'", "") +
							"_" +(i+1) +".json");
					if(f.exists()){
						f.delete();
					}
					Files.copy(f2.toPath(), f.toPath());
				}
				pw.flush();

			}catch (Exception e) {
				e.printStackTrace(pw); 
				e.printStackTrace();
				System.out.println("Here we caught!");
			}finally {
				if (ds != null)
					wr.writeCompleteFooter();

				if (param != null && param.resFile != null)
					param.resFile.close();
			}
		}
		else{
			FileInputStream fstream = new FileInputStream(getServletContext().getRealPath("/")+"/"+Config.queryFile + req.getParameter("filename")+"_query.txt");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String clusterCountFile = Config.statsDirectory + "/clusterCount-tedlingpipe.txt";

			File cc_f = new File(clusterCountFile);
			if(cc_f.exists()){
				cc_f.delete();
			}
			cc_f.createNewFile();
			int headDisplaycounter=0;
			String queryString = br.readLine();
			while(true){
				if(queryString == null) break;

				Summarize summary = new Summarize();

				DataSource ds=null;
				TreeScorer ts;
				ResultIterator ri;
				SearchParam param=null;
				PrintWriter pw = null;
				TreeResultPrinter rprint;
				long dbLoadt=0,dbLookupt=0;
				res.setContentType("text/html");
				pw = res.getWriter();
				WriteResponse wr = new WriteResponse(pw);

				String waitScreen = req.getParameter("wait");

				System.out.println("=========="+iitb.banks.util.Config.URL+"===========");
				boolean wait = false;
				try {
					startt = System.currentTimeMillis();

					param = new SearchParam(req,queryString);

					param.pw =null;
					param.log = new Logger();
					wait = waitScreen.equals("true");
					ds = DataSource.getInstanceWait(param.dsidx,wait);

					if (ds==null) {
						wr.printWaitScreen(req.getQueryString(),getClass().getName());
						return;
					}

					if (param.queryTree!=null) {
						System.out.print(param.queryTree);
					}else {
						System.out.println("Not a path query");
					}
					dbLoadt=System.currentTimeMillis()-startt;
					ts = createTreeScorer(ds, req, param.treescorer,param);
					String q = ds.findKeywordNodes(param, ts);
					dbLookupt=System.currentTimeMillis()-startt-dbLoadt;

					Runtime rt = Runtime.getRuntime();
					BackwardExpandingRI bad_ri=null; 
					Mult_BackwardExpandingRI mult_bad_ri=null; 

					System.gc();
					double free_mem=rt.freeMemory();

					switch (param.algorithm) {
					case SearchParam.ALG_BACKWARD:
						bad_ri = new BackwardExpandingRI(ds, ts, param);
						ri = new BufferedRI(bad_ri, ts, param);  
						break;
					case SearchParam.ALG_BACKWARD_MULT:
						mult_bad_ri = new Mult_BackwardExpandingRI(ds, ts, param);
						ri = new BufferedRI(mult_bad_ri, ts, param);  
						break;
					case SearchParam.ALG_BIDIREC:
						if (param.queryTree==null)
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

					//  }
					if(headDisplaycounter==0){
						wr.writeSearchHeader(param, param.dsidx);
						headDisplaycounter++;
					}
					else{
						wr.writeModifiedHeader(param, param.dsidx);
						headDisplaycounter++;
					}

					if (param.resultPrinter == SearchParam.RP_TREE)
						rprint = new TreeResultPrinter(pw, ds,param.dsidx,param);
					else if (param.resultPrinter == SearchParam.RP_PRETTYTREE)
						rprint = new PrettyResultPrinter(pw, ds,param.dsidx,param);
					else
						rprint = null;

					if(param.firstResult == 0)
						rprint.clearCache();

					int count = ri.getPosition();
					int prevTreeSize=-1;
					boolean doneRes=false;
					String prevResultInfo=null;
					ArrayList<String> allTrees = new ArrayList<String>();
					ArrayList<Tree> Trees = new ArrayList<Tree>();
					int i =0;
					while (ri.hasNext() && i < iitb.banks.util.Config.maxRslts){
						Trees.add(ri.next());
						allTrees.add(getTree(Trees.get(i).root,"Semantic", param, ds));
						i++;
					}
					double[][]editSimilarity=util.RTEDCommandLine.generateEditSimilarity(allTrees);
					ArrayList<String> clusters=iitb.banks.util.Cluster.cluster(allTrees,editSimilarity);
					//Writing the Max and Min pairs files.
					for(int j=0;j<clusters.size();j++){
						String[] treesCluster = clusters.get(j).split("-");
						int index1 = Integer.parseInt(treesCluster[0]);
						System.out.println("Index:"+index1);
						Tree tree1 = Trees.get(index1-1);
						Tree tree2=null;
						if(treesCluster.length>1){
							int index2 = Integer.parseInt(treesCluster[1]);
							tree2 = Trees.get(index2-1);
						}
						Set<Tree> maxSet=new HashSet<Tree>();
						maxSet.add(tree1);
						if(tree2!=null)
							maxSet.add(tree2);
						double minSimilarity=2;
						Set<Tree> minSet=new HashSet<Tree>();
						Tree mintree1=null;
						Tree mintree2=null;
						for(int cntt=0;cntt<treesCluster.length;cntt++){
							int treeindex1 = Integer.parseInt(treesCluster[cntt]);
							for(int cnt=cntt+1;cnt<treesCluster.length;cnt++){
								int treeindex2=Integer.parseInt(treesCluster[cnt]);
								System.out.println("Edit Sim val:"+editSimilarity[treeindex1-1][treeindex2-1]);
								if(minSimilarity>editSimilarity[treeindex1-1][treeindex2-1]){
									minSimilarity=editSimilarity[treeindex1-1][treeindex2-1];
									mintree1=Trees.get(treeindex1-1);
									mintree2=Trees.get(treeindex2-1);
								}
							}
						}
						if (mintree1!=null)
							minSet.add(mintree1);
						if (mintree2!=null)
							minSet.add(mintree2);
						try{

							String minFile1=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+queryString.replace(" ", "_").replace("'", "")+
									"_min"+(j+1)+"-1.json";
							String maxFile1=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+queryString.replace(" ", "_").replace("'", "")+
									"_max"+(j+1)+"-1.json";
							String minFile2=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+queryString.replace(" ", "_").replace("'", "")+
									"_min"+(j+1)+"-2.json";
							String maxFile2=Config.statsDirectory + "/"+Config.tedmaxMinDirectory+queryString.replace(" ", "_").replace("'", "")+
									"_max"+(j+1)+"-2.json";

							//Writing Min pairs.
							Iterator<Tree> minIter=minSet.iterator();
						int min_i=1;
						while(minIter.hasNext()){
							Tree mtmp=minIter.next();
							String writeFile = "";
							if(min_i==1)
								writeFile=minFile1;
							else
								writeFile=minFile2;

						File minf = new File(writeFile);
						if(minf.exists()){
							minf.delete();
						}
						minf.createNewFile();

						FileEdit.writeToFile(writeFile, "[\n");
						rprint.printRepTree(mtmp.root, writeFile, param);
						FileEdit.eraseLast(writeFile);
						FileEdit.writeToFile(writeFile, "]");
						File minff = new File(getServletContext().getRealPath("/")+"/stats/"+Config.tedmaxMinDirectory+queryString.replace(" ", "_").replace("'", "")+
								"_min"+(j+1)+"-"+min_i+".json");

						if(minff.exists()){
							minff.delete();
						}
						Files.copy(minf.toPath(), minff.toPath());
						min_i++;
						}






							//Writing MAX pairs
							Iterator<Tree> maxIter=maxSet.iterator();
							int max_i=1;
							while(maxIter.hasNext()){
								Tree mtmp=maxIter.next();
								String writeFile = "";
								if(max_i==1)
									writeFile=maxFile1;
								else
									writeFile=maxFile2;

								File mf = new File(writeFile);
								if(mf.exists()){
									mf.delete();
								}
								mf.createNewFile();

								FileEdit.writeToFile(writeFile, "[\n");
								rprint.printRepTree(mtmp.root, writeFile, param);
								FileEdit.eraseLast(writeFile);
								FileEdit.writeToFile(writeFile, "]");
								File mff = new File(getServletContext().getRealPath("/")+"/stats/"+Config.tedmaxMinDirectory+queryString.replace(" ", "_").replace("'", "")+
										"_max"+(j+1)+"-"+max_i+".json");

								if(mff.exists()){
									mff.delete();
								}
								Files.copy(mf.toPath(), mff.toPath());
								max_i++;
							}

							//Writing representative file.
							String representativeFile=Config.statsDirectory + "/"+Config.tedrepDirectory+queryString.replace(" ", "_").replace("'", "")+
									"_rep"+(j+1)+".json";
							File rf = new File(representativeFile);
							if(rf.exists()){
								rf.delete();
							}
							rf.createNewFile();

							FileEdit.writeToFile(representativeFile, "[\n");
							rprint.printRepTree(tree1.root, representativeFile, param);
							FileEdit.eraseLast(representativeFile);
							FileEdit.writeToFile(representativeFile, "]");

							File repff = new File(getServletContext().getRealPath("/")+"/stats/"+Config.tedrepDirectory+queryString.replace(" ", "_").replace("'", "")+
									"_rep"+(j+1)+".json");
							if(repff.exists()){
								repff.delete();
							}
							Files.copy(rf.toPath(), repff.toPath());

						}catch(Exception e){
							e.printStackTrace();
						}

					}


					FileEdit.writeToFile(clusterCountFile, queryString+"\t"+clusters.size()+"\n");
					pw.println("<script type=\"text/javascript\">");

					for(i=1;i<=clusters.size();i++)
					{
						String function_name =queryString.replace(" ", "_").replace("\"", "")+ "_show_Cluster"+i+"()";
						String button_name = queryString.replace(" ", "_").replace("\"", "")+"_Button_Cluster"+i;
						String cluster_name=queryString.replace(" ", "_").replace("\"", "")+"_Cluster"+i;

						pw.println("function "+ function_name+"{");
						pw.println("if(document.getElementById('"+button_name+"').innerHTML=='Hide Cluster')");
						pw.println("{");
						pw.println("document.getElementById('"+button_name+"').innerHTML = 'Show Cluster'");
						pw.println("document.getElementById('"+cluster_name+"').style.display=\"none\"");
						pw.println("} else");

						pw.println("{");
						for(int j=1;j<=clusters.size();j++)
						{
							if(j==i)
							{
								pw.println("document.getElementById('"+button_name+"').innerHTML = 'Hide Cluster'");
								pw.println("document.getElementById('"+ cluster_name+"').style.display=\"block\"");
							}
							else
							{
								String button_name2=queryString.replace(" ", "_").replace("\"", "")+"_Button_Cluster"+j;
								String cluster_name2=queryString.replace(" ", "_").replace("\"", "")+"_Cluster"+j;	

								pw.println("document.getElementById('"+button_name2+"').innerHTML = 'Show Cluster'");
								pw.println("document.getElementById('"+ cluster_name2+"').style.display=\"none\"");
							}
						}
						pw.println("}}");
					}

					pw.println("</script>");

					pw.flush();
					if (count == 0) {
						pw.println("<div class=qinfo>"
								+ q
								+ " <i>Click on keywords to select or filter nodes.</i></div>");
					}
					pw.println("<div style=\"overflow: auto;width: 40%; height:100%;float:left\">");
					for(i =0; i< clusters.size(); i++){
						pw.flush();

						String[] treesInThisCluster = clusters.get(i).split("-");
						System.out.println(clusters.get(i));
						int index = Integer.parseInt(treesInThisCluster[0]);
						System.out.println(clusters.get(i));
						Tree tree = Trees.get(index-1);

						pw.println("<br/><br/><table width=98% cellspacing=0 cellpadding=0 style=\"font-size: 10pt; background-color:#eeeeee; border: 1px solid #808080; position: relative; z-index:10\">");
						pw.println("<tr><td><b>Cluster:</b> " + (i+1)
								+ "</td></tr>");
						pw.println("</table></br></br>");

						//	rprint.printTree(tree.root, 1);
						pw.println("<div style=\"overflow: auto;display:block;width: 100%; height:30%\" id=\""+queryString.replace(" ", "_").replace("\"", "")+"ClusterRep"+(i+1)+"\">");
						String jsonRepFile = Config.host+"/TreeEdit/MyAns.html?answerFile=TreeEdit/stats/"+Config.tedrepDirectory+
								queryString.replace(" ", "_").replace("'", "")+"_rep"+(i+1)+".json";
						pw.println("<iframe width=\"100%\" height=\"100%\" src="+jsonRepFile + "></iframe>");
						pw.println("</div>");
						pw.println("<br />");

						String button_name=queryString.replace(" ", "_").replace("\"", "")+"_Button_Cluster"+(i+1);
						String function_name=queryString.replace(" ", "_").replace("\"", "")+"_show_Cluster"+(i+1)+"()";
						pw.println("<table align=\"left\" style=\"font-size:11pt\"><tr><td><a id=\""+ button_name+"\" onclick=\""+function_name+"; return false;\" href=\"#\">Show Cluster</a></td>");
						pw.println("</tr></table>");

						pw.println("<br />");
						pw.flush();
					}

					pw.println("</div>");
					for(i =0; i< clusters.size(); i++){
						pw.println("<div style=\"overflow: auto;display:none;width: 49%; height:100%; float:right\" id=\""+queryString.replace(' ', '_').replace("\"", "")+"_Cluster"+(i+1)+"\">");

						String jsonFile =Config.host+"TreeEdit/MyAns.html?answerFile=TreeEdit/stats/TED_clusterFiles/"+
								queryString.replace(" ", "_").replace("'", "")+"_"+(i+1)+".json";
						pw.println("<iframe width=\"100%\" height=\"100%\" src="+jsonFile + "></iframe>");
						pw.println("</div>");
						String filename = getServletContext().getRealPath("/")+"/stats/"+"TED_clusterFiles/"+queryString.replace(" ", "_").replace("'", "") +
								"_" +(i+1) +".json";
						File f22 = new File(filename);
						if(f22.exists()){
							f22.delete();
						}
						f22.createNewFile();
						FileEdit.writeToFile(filename, "[\n");
						String[] treesInThisCluster = clusters.get(i).split("-");
						for(int j =0; j< treesInThisCluster.length; j++){

							int	index = Integer.parseInt(treesInThisCluster[j]);
							Tree tree = Trees.get(index-1);
							rprint.printTree(tree.root, queryString, 1,filename, param);
						}


						FileEdit.eraseLast(filename);
						FileEdit.writeToFile(filename, "]");


						rprint.close();

						param.log.close();
						File f = new File(Config.statsDirectory+"TED_clusterFiles/" +
								queryString.replace(" ", "_").replace("'", "") +
								"_" +(i+1) +".json");

						if(f.exists()){
							f.delete();
						}
						Files.copy(f22.toPath(), f.toPath());
					}
					pw.flush();

				}catch (Exception e) {
					e.printStackTrace(pw); 
					e.printStackTrace();
					System.out.println("Here we caught!");
				}finally {
					queryString = br.readLine();
					if (ds != null && queryString==null)
						wr.writeCompleteFooter();

					if (param != null && param.resFile != null)
						param.resFile.close();
				}
			}
		}
	}


	protected String getTree(MyTreeNode myTreeNode, String type, SearchParam param, DataSource ds) 
			throws IOException, SQLException{

		Statement stmt = ds.createStatement();

		int nodeid = myTreeNode.vertexIndex ;
		int tabid = ds.G.getTableId(nodeid);
		String tab = ds.getTableName(tabid);

		String subTree = "{";
		if(type == "Semantic"){
			subTree += tab;
		}

		if(type == "Labeled"){
			FileInputStream fstream = new FileInputStream(Config.displayColumns);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String str = "";
			String displayCol = "";
			while((str=br.readLine()) !=null){
				StringTokenizer st = new StringTokenizer(str, " ");
				String dbname = st.nextToken();
				if(dbname.equals(ds.schema)){
					while (st.hasMoreTokens()) {
						displayCol += st.nextToken();
					}
					break;
				}
			}

			ResultSet rs1=null;
			ResultSetMetaData rsmd1=null;
			String sql ="";
			String rowid = ds.G.getRowId(nodeid);
			String[] pkey = ds.getTablePrimaryKey(tabid);
			if(pkey == null){
				sql = "SELECT * " +
						" FROM " + tab +
						" WHERE banks_node_id=" + nodeid;

			}else{
				sql = "SELECT * " +
						" FROM " + tab +
						" WHERE "+ DataSource.getCondition(pkey, rowid, tab);
			}
			try {
				rs1 = stmt.executeQuery(sql);

				rsmd1 = rs1.getMetaData();
				int count = rsmd1.getColumnCount();

				rs1.next();
				String temp="";
				//---------------------------
				int nKeywords = param.kws.size();
				String[] keywords  = new String[nKeywords];
				for(int i =0;i<nKeywords; i++){
					Keyword kw = (Keyword) param.kws.get(i);
					keywords[i]= kw.phrase.toString().replace("]", "").replace("[", "");
				}
				//-------------------------------
				for (int i=1; i<= count; i++) {
					String columnName = rsmd1.getColumnName(i);
					if(ds.G.displayCols.get(tab+"#"+columnName)==0)
						continue;
					String s = rs1.getString(i);
					if (s == null) s = " NULL";
					s.replace("",",");
					for(int j =0;j<nKeywords; j++){
						if((s.toLowerCase().contains(keywords[j].toLowerCase()) && !temp.contains(columnName)) ||
								displayCol.toLowerCase().contains(columnName.toLowerCase())){
							temp += s;
							break;
						}
					}
				}
				rs1.close();
				subTree += temp;
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		MyTreeNode childNode = null;

		for (childNode = myTreeNode.firstChild; childNode != null; childNode = childNode.nextSibling) {			
			subTree += getTree(childNode, type, param, ds);
		}
		return subTree+"}";
	}


	private int getNodeId(MyTreeNode myTreeNode, Integer keywordIndex) {


		if (myTreeNode.keywordIndex.contains(keywordIndex)) {
			return myTreeNode.vertexIndex;
		}
		MyTreeNode childNode = null;
		for (childNode = myTreeNode.firstChild; childNode != null; childNode = childNode.nextSibling) {
			int tmp=getNodeId(childNode,keywordIndex);
			if (tmp!=-1) return tmp;
		}

		return -1;

	}


	private void getIdVector(MyTreeNode myTreeNode, Vector v) {


		v.add(new Integer(myTreeNode.vertexIndex));
		MyTreeNode childNode = null;
		for (childNode = myTreeNode.firstChild; childNode != null; childNode = childNode.nextSibling) {
			getIdVector(childNode,v);
		}

	}


	private TreeScorer createTreeScorer(DataSource ds,HttpServletRequest req, String treescorer,SearchParam sp)
	{
		int meanType=0, combType=0, nsType=0, esType=0, esComb=0,psType=0;
		float lambda=0.0f, gamma=0.0f;
		String par;
		TreeScorer ts;


		par = req.getParameter("meanType");
		if ("arithmetic".equalsIgnoreCase(par))
			meanType = ActivationTreeScorer.MEAN_ARITHMETIC;
		else if ("geometric".equalsIgnoreCase(par))
			meanType = ActivationTreeScorer.MEAN_GEOMETRIC;
		else if("harmonic".equalsIgnoreCase(par))
			meanType = ActivationTreeScorer.MEAN_HARMONIC;
		else //default values
			meanType = ActivationTreeScorer.MEAN_HARMONIC;

		par = req.getParameter("combType");
		if ("arithmetic".equalsIgnoreCase(par))
			combType = TreeScorer.COMB_ARITHMETIC;
		else if("geometric".equalsIgnoreCase(par))
			combType = TreeScorer.COMB_GEOMETRIC;
		else 
			combType = TreeScorer.COMB_GEOMETRIC;

		par = req.getParameter("nsType");
		if ("linear".equalsIgnoreCase(par))
			nsType = TreeScorer.NS_LINEAR;
		else if("log".equalsIgnoreCase(par))
			nsType = TreeScorer.NS_LOG;
		else
			nsType = TreeScorer.NS_LOG;

		par = req.getParameter("psType");
		if ("linear".equalsIgnoreCase(par))
			psType = TreeScorer.NS_LINEAR;
		else if("log".equalsIgnoreCase(par))
			psType = TreeScorer.NS_LOG;
		else
			nsType = TreeScorer.NS_LOG;


		par = req.getParameter("esType");
		if ("log".equalsIgnoreCase(par))
			esType = ConventionalTreeScorer.ES_LOG;
		else if("power".equalsIgnoreCase(par))
			esType = ConventionalTreeScorer.ES_POWER;
		else
			esType = ConventionalTreeScorer.ES_POWER;

		par = req.getParameter("esComb");
		if ("sum".equalsIgnoreCase(par))
			esComb = ConventionalTreeScorer.EC_SUM;
		else if("prod".equalsIgnoreCase(par))
			esComb = ConventionalTreeScorer.EC_PROD;
		else
			esComb = ConventionalTreeScorer.EC_PROD;


		par = req.getParameter("lambda");
		if (par != null) {
			try {
				lambda = Float.parseFloat(par);
			}
			catch (NumberFormatException nfe) {
				lambda = 0.2f;
			}
		}
		else
			lambda = 0.2f;

		par = req.getParameter("gamma");
		if (par != null) {
			try {
				gamma = Float.parseFloat(par);
			}
			catch (NumberFormatException nfe) {
				gamma = 0.5f;
			}
		}
		else
			gamma = 0.5f;


		sp.ats=new ActivationTreeScorer(ds, 
				meanType,
				combType,
				nsType,
				lambda,
				psType
				);

		sp.cts=new ConventionalTreeScorer(ds,
				combType,
				nsType,
				esType,
				esComb,
				gamma,
				lambda
				);

		if((treescorer != null && treescorer.equalsIgnoreCase("probability"))) {
			ts= new ActivationTreeScorer(ds, 
					meanType,
					combType,
					nsType,
					lambda,
					psType
					);
		}else{ 
			ts= new ConventionalTreeScorer(ds,
					combType,
					nsType,
					esType,
					esComb,
					gamma,
					lambda
					);
		}

		return ts;
	}


}
