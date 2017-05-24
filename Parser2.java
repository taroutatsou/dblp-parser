package dblpParser;

import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


public class Parser2 {
    public static void main(String[] args){
        try {

            // Path to config file
            File configFile = new File("./config.xml");

            // Initialize the config handler
            UserHandler configHandler = new ConfigHandler();

            // Parse config
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser configParser = factory.newSAXParser();
            configParser.parse(configFile, configHandler);

            // // Get MAX_YEAR from command line
            // int maxYear = Integer.parseInt(args[0]);

            // Initialize the dbml xml handler
            UserHandler userhandler = new UserHandler(configHandler);

            // Path to dblp.xml
            File inputFile = new File(configHandler.getDataPath());

            // Parse dblp xml
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(inputFile, userhandler);

            // Initialize output file writers
            PrintWriter idAndSen = null; // author id and seniority (numerical or nominal)
            PrintWriter idAndName = null; // author id and name
            PrintWriter idAndCommonConfs = null; // author id and most common conferences (multiple)
            PrintWriter idAndCommonFields = null; // author id and most common fields (multiple)

            try{
                idAndCommonConfs = new PrintWriter("./"+configHandler.getMAX_YEAR()+"/"+configHandler.getCurrentDateTime+"idAndCommonConfs.txt","UTF-8");
                idAndCommonFields = new PrintWriter("./"+configHandler.getMAX_YEAR()+"/"+configHandler.getCurrentDateTime+"idAndCommonFields.txt","UTF-8");
                idAndSen = new PrintWriter("./"+configHandler.getMAX_YEAR()+"/"+configHandler.getCurrentDateTime+"seniority.txt","UTF-8");
                idAndName = new PrintWriter("./"+configHandler.getMAX_YEAR()+"/"+configHandler.getCurrentDateTime+"idToName.txt","UTF-8");
            } 
            catch (Exception e){
                e.printStackTrace();
            }

            // Get the persons collection
            PersonCollection personCollection = userhandler.getPersonCollection();

            // For every person fill up the output files
            for (Person p : personCollection.getCollection()){

                idAndName.println(p.getID() + "\t" + p.getName());

                idAndSen.println(p.getID() + "\t" + p.getAvg());

                idAndCommonConfs.print(p.getID());
                ArrayList<Pair> confs = p.getXMostCommonConferences(configHandler.getNumberOfSkillsPerWorker());
                for(Pair pr : confs){
                    idAndCommonConfs.print("\t" + pr.getConf());
                }
                idAndCommonConfs.print("\n");
                
                idAndCommonFields.print(p.getID());
                ArrayList<Pair> fields = p.getXMostCommonFields(configHandler.getNumberOfSkillsPerWorker());
                for(Pair pr : fields){
                    idAndCommonFields.print("\t" + pr.getConf());
                }
                idAndCommonFields.print("\n");
            }

            // Close output writers
            idAndSen.close();
            idAndName.close();
            idAndCommonFields.close();
            idAndCommonConfs.close();

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ConfigHandler extends DefaultHandler{
    int MAX_YEAR;
    String datasetPath;
    int numberOfSkillsPerWorker;
    String pathToConferencesList;
    String currentDateTime;

    int getMAX_YEAR(){
        return MAX_YEAR;
    }

    String getDataPath(){
        return datasetPath;
    }

    String getConferencesListPath(){
        return pathToConferencesList;
    }

    String getCurrentDateTime(){
        return currentDateTime;
    }

    int getNumberOfSkillsPerWorker(){
        return numberOfSkillsPerWorker;
    }

    void ConfigHandler(){

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Date date = new Date();
        currentDateTime = dateFormat.format(date);

    }

    @Override
    public void startElement(String uri, String localName, String eName, Attributes attributes) throws SAXException {
        content="";
    }

    @Override
    public void endElement(String uri, String localName, String eName) throws SAXException {
        if(eName.equals("max_year")){
            MAX_YEAR = content;
            content="";
        }
        else if(eName.equals("path_to_dblp_xml")){
            datasetPath = content;
            content="";
        }
        else if(eName.equals("number_of_sklls_per_worker")){
            numberOfSkillsPerWorker = content;
            content="";
        }
        else if(eName.equals("path_to_conferences_list")){
            pathToConferencesList = content;
            content="";
        }
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        content += new String(ch, start, length);
    }
}

class UserHandler extends DefaultHandler {
    int MAX_YEAR;
    boolean insideConf = false;
    int inproceedingsCount = 0;
    String content;
    String year;
    boolean insidePerson;
    int maxID = 0;
    ArrayList<Integer> persons;
    String elementName;
 
    PersonCollection personCollection = new PersonCollection();
    String confName;
    HashSet<String> conferences = new HashSet<String>(); // a set containing all the required conferences
    HashMap<String,String> confToField = new HashMap<String,String>(); // a mapping of conferences to their respected fields

    PrintWriter edgeListWriter;

    public UserHandler(int configObject){

        MAX_YEAR = configObject.getMAX_YEAR();

        try{
            BufferedReader br = new BufferedReader(new FileReader(configObject.getConferencesListPath()));

            String line;
            while((line = br.readLine()) != null){
                String[] parts = line.split(" ");
                
                conferences.add(parts[0]);
                confToField.put(parts[0],parts[1]);
            }
            
            edgeListWriter = new PrintWriter("./"+configObject.getMAX_YEAR()+"/"+configObject.getCurrentDateTime+"edgeList.txt","UTF-8");

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public PersonCollection getPersonCollection(){
        return personCollection;
    }

    @Override
    public void startElement(String uri, String localName, String eName, Attributes attributes) throws SAXException {
        elementName = eName; // looking for inproceedings
        if (insidePerson = (elementName.equals("author") || elementName.equals("editor"))) {
            content = "";
            return;
        }

        // START OF INPROCEEDINGS ELEMENT
        if((attributes.getLength()>0) && k != null){
            year = Integer.parseInt(attributes.getcontent("mdate").split("-")[0]);
            String k = attributes.getcontent("key").split("/");	
            String pubType = k[0]; // conf
            confName = k[1]; // examples: kdd, www etc

            if( year < MAX_YEAR || !conferences.contains(confName)) { // if older than MAX_YEAR and conference not included in the specified list, continue
                return;
            }

            if(pubType.equals("conf") && elementName.equals("inproceedings")){
                insideConf = true;
                
                persons = new ArrayList<Integer>(); // create new persons array to put the authors of the new paper
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String eName) throws SAXException {

        // If the closing element is an author and insideConf is ON process the data
        if (eName.equals("author") && insideConf){

            // Find person from collection (content is the authors name in this case)
            Person p = PersonCollection.getPersonIfExists(content);

            // If the person doesnt exist in the Collection put him in
            if (p == null){
                p = PersonCollection.putPersonInCollection(content);
            }
            
            // Add conference to his conferences list, add Field and year
            p.addConference(confName);
            p.addField(confToField.get(confName));
            p.addYear(year);

            // Add person to persons i.e. the array of people in this specific paper
            persons.add(p.getID());

            // Toggle insidePerson to stop recording the content
            insidePerson=false;
        }
        // Otherwise if closing element is 
        else if (eName.equals(elementName) && insideConf){		// closing element is inproceedings
            inproceedingsCount = inproceedingsCount + 1;
            System.out.print(inproceedingsCount + "\r");
            
            // persons is an arraylist of all ids of persons in the currently closing inproceedings record.

            if (persons.isEmpty()){
                return;
            }
            
            printEdges(persons);

            insideConf = false;
        }
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        if (insidePerson)
            content += new String(ch, start, length);
    }

    void printEdges(ArrayList<Integer> list){
        for(int i=0;i<list.size();i++){
            for(int j=i+1;j<list.size();j++){
                int one = list.get(i);
                int two = list.get(j);
                if (one <= two){
                    edgeListWriter.println(one + "\t" + two);
                }
                else{
                    edgeListWriter.println(two + "\t" + one);
                }
            }
        }
    }
}
