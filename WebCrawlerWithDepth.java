
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.io.IOException;
import java.io.InputStreamReader;
import static java.lang.Math.log;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class WebCrawlerWithDepth {
    
    private static final int MAX_DEPTH = 2;
    public static DB db = new DB();
    private HashSet<String> links;
    int flag=0;

    public WebCrawlerWithDepth() throws SQLException, IOException{
        
        //to reset data for every search
        //for all URLs scrapped
        db.runSql2("TRUNCATE Record;");
        //for URLs which have the keyword or its synonyms
        db.runSql2("TRUNCATE search;");
        
    }

    public void getPageLinks(String URL, int depth, String search)  throws SQLException, IOException{
        
        //thesaurus to get results for the synonyms as well
        //till now its only for some words
        //but can increased accordingly
        String [][] thesaurus = {
            {"research","analysis","investigation"},
            {"education","training","learning"},
            {"php","php","php"}
        };

        String search1=null,search2=null;
        int i=0;
        for (i=0;i<=2;i++){
            if (search.compareTo(thesaurus[i][0])==0){
                //System.out.println("inside");
                search1=thesaurus[i][1];
                search2=thesaurus[i][2];
                break;
            }
        }
        
        //finding synonyms
        if (flag==0){
            System.out.println("\nSynonyms of the searched keyword: ");
            System.out.println(search1);
            System.out.println(search2);
            System.out.println();
            flag=1;
        }
        
        //starting the process
        String sql = "select * from Record where URL = '"+URL+"'";
	ResultSet rs = db.runSql(sql);
        
        if(rs.next()){
            
                //the URL is already present 
                sql = "UPDATE `Web`.`Record` SET `count`=`count`+1 WHERE URL = '"+URL+"'";
                PreparedStatement stmt = db.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.execute();

                //damping factor
                double df = 0.6;
                
                //getting value of inlinks, outlinks and cosine similarity
                double inlink = 0, outlink = 0, cs = 0;
                sql = "select `count`, `outlinks`, `tfitf` from `Web`.`Search` where URL = '"+URL+"'";
                rs = db.runSql(sql);
                if (rs.next()){
                    inlink = rs.getDouble("count");
                    outlink = rs.getDouble("outlinks");
                    cs = rs.getDouble("tfitf");
                    }

                //updating rank everytime page is referenced
                //dynamic ranking
                //because popularity for that page changes
                sql = "UPDATE `Web`.`Search` SET `rank`= "+((double)java.lang.Math.pow(df,outlink)*inlink*cs*outlink)+" WHERE URL = '"+URL+"'";
                //System.out.println(sql);
                System.out.print("Updating Rank - > ");
                System.out.println(URL);
                stmt = db.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.execute();
	
                    }else{
            
                        //web crawler works uptill a fixed depth
                        //to correct problem of the web crawler getting lost
			if (depth<MAX_DEPTH){
                        //store the URL to database to avoid parsing again
                        System.out.println(URL);
                        if (URL != null || URL.length() != 0){
                            sql = "INSERT INTO  `Web`.`Record` " + "(`URL`,`count`) VALUES " + "(?,?);";
                            //System.out.println(sql)
                            System.out.print("Inserting into Record -> ");
                            System.out.println(URL);
                            PreparedStatement stmt = db.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                            //adding URL value and initializing the count value
                            stmt.setString(1, URL);
                            stmt.setInt(2, 1);
                            stmt.execute();

                            //get useful information from the web pages
                            //scrapping the web pages
                            Connection conn = Jsoup.connect(URL).userAgent("Chrome").timeout(10000);
                            Document doc = conn.get();
                            if (conn.response().contentType().contains("text/html")){
                                //if web page contains search word - it's impostant
                                //we also search for presence of synonyms as well
                                if(doc.text().contains(search) || doc.text().contains(search1) || doc.text().contains(search2)){
                                    System.out.println("GOT APPROPRIATE PAGE");
                                    System.out.println();

                                    System.out.println("WORKING FOR COSINE SIMILARITY");
                                    String text = doc.body().text();

                                    //total term count
                                    int count_main;
                                    //required term count
                                    int count;

                                    //reading text in HTML files
                                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))))) {
                                        String line;
                                        count_main = 0;
                                        count = 0;
                                        while ((line = reader.readLine()) != null) {
                                            String[] words = line.split("[^A-ZÃƒâ€¦Ãƒâ€žÃƒâ€“a-zÃƒÂ¥ÃƒÂ¤ÃƒÂ¶]+");
                                            for (String word : words) {
                                                count_main++;

                                                //to avoid stop words
                                                if (search.equals("the") || search.equals("and") || search.equals("a")){
                                                    continue;
                                                }

                                                //important words
                                                if (word.equals(search) || word.equals(search1) || word.equals(search2)) {
                                                    count++;
                                                }
                                            }
                                        }
                                    }

                                    //calculating tf and itf
                                    double tf,itf,tfitf;
                                    tf=(double)count/count_main;
                                    if (count == 0) {
                                        itf=0;
                                        //throw new ArithmeticException();
                                    }
                                    else {
                                        itf=(double)log(count_main/count);
                                    }

                                    //same as cosine similarity
                                    tfitf=tf*itf;

                                    //inserting values to the database  
                                    sql = "INSERT INTO `Web`.`search`(`URL`, `tf`, `itf`, `tfitf`, `rank`,`count`,`outlinks`) VALUES('"+URL+"','"+tf+"','"+itf+"','"+tfitf+"',0,0,0);";
                                    //System.out.println(sql);
                                    System.out.print("Inserting into Search -> ");
                                    System.out.println(URL);
                                    stmt = db.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                                    stmt.execute();

                                    //updating count field which is for popularity and inlinks
                                    sql = "UPDATE `Web`.`search` SET `count` = (SELECT `count` from `Web`.`Record` where `URL`='"+URL+"')";
                                    //System.out.println(sql);
                                    stmt = db.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                                    stmt.execute();

                                }

                                //get all links and recursively call the processPage method
                                Elements questions = doc.select("a[href]");

                                //update number of outlinks for a page
                                sql = "UPDATE `Web`.`search` SET `outlinks` = "+questions.size()+" WHERE URL = '"+URL+"'";
                                //System.out.println(sql);
                                stmt = db.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                                stmt.execute();                        

                                //perform same function recursively for all pages
                                for(Element link: questions){
                                        //System.out.println(questions.size());
                                        if(link.attr("href").contains(search))
                                                getPageLinks(link.attr("abs:href"),depth,search);
                                }

                                
                                //damping factor
                                double df = 0.6;

                                //getting value of inlinks, outlinks and cosine similarity
                                double inlink = 0, outlink = 0, cs = 0;
                                sql = "select `count`, `outlinks`, `tfitf` from `Web`.`Search` where URL = '"+URL+"'";
                                rs = db.runSql(sql);
                                if (rs.next()){
                                    inlink = rs.getDouble("count");
                                    outlink = rs.getDouble("outlinks");
                                    cs = rs.getDouble("tfitf");
                                }

                                //updating rank everytime page is referenced
                                //dynamic ranking
                                //because popularity for that page changes
                                sql = "UPDATE `Web`.`Search` SET `rank`= "+((double)java.lang.Math.pow(df,outlink)*inlink*cs*outlink)+" WHERE URL = '"+URL+"'";
                                System.out.print("Updating Rank - > ");
                                System.out.println(URL);
                                stmt = db.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                                stmt.execute();

                            }
                        
                        }
                        
                        //after each layer depth is increamented
                        depth++;
		}
        }
    }

    public static void main(String[] args) throws SQLException, IOException {
        
        //new WebCrawlerWithDepth().getPageLinks("http://study.com/academy/lesson/what-is-research-definition-purpose-typical-researchers.html", 0,"research");
        new WebCrawlerWithDepth().getPageLinks("https://www.w3schools.com/", 0,"php");
        
    }
    
}