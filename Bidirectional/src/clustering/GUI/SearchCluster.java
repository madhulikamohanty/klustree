package clustering.GUI;
import iitb.banks.datasource.DataSource;
import iitb.banks.util.Config;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import clustering.utils.SearchAndCluster;
/**
 *  The servlet to search bidirectionally and cluster keyword search (BANKS) results.
 *  
 *  @author Madhulika Mohanty(madhulikam@cse.iitd.ac.in)
 *
 */
public class SearchCluster extends HttpServlet {
	
	private static final long serialVersionUID = 8583645502994563655L;
	

	public void init(ServletConfig sc) throws ServletException {
		super.init(sc);
		System.out.println("-----------------------------------------------");
		System.out.println(" Super Fast BANKS Search initializing     ");
		System.out.println("-----------------------------------------------");
	}

	public void destroy() {
		try {
			DataSource.close();
		} catch (Exception sqe) {
			System.out.println(sqe);
			sqe.printStackTrace();
		}
	}

	public void doPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException, ServletException {
		doGet(req, res);
	}

	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws IOException, ServletException {
		
		res.setContentType("text/html");
		PrintWriter pw = res.getWriter();
		SearchAndCluster sac = new SearchAndCluster(req,pw);
		if(req.getParameter("q_type").equals("file")){
			String qFileName = getServletContext()+"/"+Config.queryFile + req.getParameter("filename")+"_query.txt";
			sac.processFile(qFileName);
		}
		else{
			String QUERY = req.getParameter("q");
			sac.processBrowserQuery(QUERY);
		}
	}

	
}