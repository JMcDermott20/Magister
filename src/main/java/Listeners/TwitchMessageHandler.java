package Listeners; /**
 *  @author Tacet Nox
 *
 * Event Listener for Twitch.tv, most handling is done here, whispers are handled in WhisperHandler
 */

import Util.*;
import Util.SQLTools.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TwitchMessageHandler extends ListenerAdapter{

    //Init DB connection and related calls
    private Connection conn = new SQLConnect().connect();
    private EDITSubname editSubName = new EDITSubname();
    private ADDSubname addSubname = new ADDSubname();
    private GETSubname getSubName = new GETSubname();
    private GoogleSheetsReader SheetReader = new GoogleSheetsReader();



    //initializing the command users cooldown arraylist and arraylist for the rules list (if ever used again)
    private ArrayList<String> commandUse = new ArrayList<>();
    private ArrayList<String> rules = new ArrayList<>();

    private ArrayList<String> timestamps = new ArrayList<>();

    //Initializing cooldowns for commands
    private long secondsSinceCommand = System.nanoTime();
    long secondsSinceRuleCommand = System.nanoTime();
    private long secondsSinceCleared = System.nanoTime();
    private long secondsSinceCacheOut = System.nanoTime();

    //Initializing exporting requirement variable
    private int writeNeeded = 0;

    //Initializing Twitch API auth variables
    private String Api0Auth = new GetApi0Auth().getAuth(conn);
    private String clientID = new GetApiClientID().getAuth(conn);


    /*
     * Small section to potentially be a message queue.
     * Needs to be actively tested,
     * need to keep the whisper limit under control, but need to bring in Unknown Event
     *
     * Currently only being used by the cmdUse function
     */
    final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<String>();
    final BlockingQueue<MessageEvent> eventQueue = new LinkedBlockingQueue<MessageEvent>();

    Thread running = new Thread(() -> {
        while (true) {
            try {
                String message = messageQueue.take();
                MessageEvent ev = eventQueue.take();
                ev.respondWith(message);
                Thread.sleep(1000);
                //handle the data
            } catch (InterruptedException e) {
                System.err.println("Error occurred:" + e);
            }
        }
    });

    //Used for being able to globally initialize the API auth vars, does this break anything?
    public TwitchMessageHandler() throws SQLException {
    }


    /**
     *  Overrides the default empty onMessage function. This is where most message processing goes,
     *  as well as where commands are parsed and the corresponding functions are called.
     *
     * TODO: Really should shunt all the commands into their own files like the discord bot
     * @author Tacet Nox
     * {@inheritDoc}
     */
    public void onMessage(final MessageEvent event) throws Exception {

        System.gc();

        Date currDate = new Date();
        String tmstmp  = new SimpleDateFormat("yyyy-MM-dd").format(currDate);
        System.out.println(tmstmp + " ~~~ " + event.getUser().getNick() + " :: " + event.getMessage());


        //For getting raw line capabilities without casting later on, realistically is a waste of resources but
        //it's minimal
        Event whispEna = (Event) event;

        String message = event.getMessage(); //Getting just the message
        String sender = event.getUser().getNick();
        //System.out.println(sender);
        String line = event.toString(); // getting event line to pull tags from

        String[] tags = line.split(","); // splitting on commas
        int modtag = tags.length-1; // last tag position, SHOULD be user-type
        String tag = tags[modtag]; // getting value out of tags[] at position modtag
        String usertype = tag.substring(0, tag.length()-2);

        int isMod = isMod(usertype); // 1 for mod, 0 for not
        long timeOfMessage = System.nanoTime(); //Pulling nanosecond time of the message received
        long elapsed = TimeUnit.NANOSECONDS.toSeconds(timeOfMessage - secondsSinceCommand);
        long timeSinceCleared = TimeUnit.NANOSECONDS.toSeconds(timeOfMessage - secondsSinceCleared);
        long timeSinceCacheOut = TimeUnit.NANOSECONDS.toSeconds(timeOfMessage - secondsSinceCacheOut);

        if(sender.equalsIgnoreCase("tritemare"))isMod = 1;//Channel hosts don't have tags, allowing him to have "mod" privelages lol

        if(commandUse.size() > 0 && timeSinceCleared > 120){
            System.out.println("Recent Users list cleared! ~~~ " + tmstmp);
            commandUse.clear();
            secondsSinceCleared = System.nanoTime();

        }

        //System.out.println(timeSinceCacheOut);
        if(timeSinceCacheOut > 3600){//If it's been more than a half hour, check if necessary.
            if(writeNeeded > 0){
                secondsSinceCacheOut = System.nanoTime();
                cacheOut();
            }else{
                timeSinceCacheOut = 500;
            }

        }

		/*
		 *
		 * TIMESTAMP GENERATION
		 * EXAMPLE OF MOOBOT OUTPUT
		 * TimeStamp: @3h 3m 46s : Battlerite : turn around dumbassssss ( Tritemare )
		 *
		 */

        if(sender.equalsIgnoreCase("moobot") || sender.equalsIgnoreCase("jaxxx_ol")){
            if(message.startsWith("TimeStamp: ")){
                // Trimming the command out and any trailing whitespace
                String newStamp = message.substring(11).trim();
                String temptimeCode = newStamp.split(":")[0];
                temptimeCode = temptimeCode.substring(1);
                String timeNoSpace = temptimeCode.replaceAll("\\s+", "");

                //If a TimeStamp happens to fall in the first 60 seconds
                //after an hour rolls, so before the 1st minute has passed,
                //Moobot wouldn't send any minute count, which would break the link
                if(!timeNoSpace.contains("m"))
                {
                    String noMin = "0m";
                    String beforeMin = timeNoSpace.substring(0, timeNoSpace.indexOf('h')+1);
                    String afterMin = timeNoSpace.substring(timeNoSpace.indexOf('h')+1);
                    timeNoSpace = beforeMin.concat(noMin.concat(afterMin));
                }


                // Making a datetime and formatting said datetime
                String insDateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

                //Pulling ID needed for highlight URL from the API
                String urlIDThing = newStamp(); // get only the most recent (current active) video ID
                //The finished URL containing the VOD ID and the given time for the highlight
                if(urlIDThing.equalsIgnoreCase("OHSHITNO")){
                    event.respondWith("Running into some problems on the back-end making the link, give JaxXx_oL a nudge to let him known there's a problem.");

                } else{

                    String theUrl = "https://www.twitch.tv/tritemare/manager/highlighter?" + urlIDThing + "&t=" + timeNoSpace;

                    //System.out.println(theUrl);
                    //Just some cheeky response to show that we've gotten this far. Can also use this to determine latency on API calls
                    event.respondWith("Recordin' all them goody-goodies, storing it in the treasure box triteL triteH");

                    //Adding the timestamp to the list to be appended to the timestamp file on next cache out
                    SheetReader.GoogleSheetsReader(insDateStr + " ~~~ " + newStamp + " ~~~ " + theUrl);
                    //Setting the need for a cacheout to true
                    //NOW unneeded if it adds to the sheets page immediately
                    //writeNeeded++;
                }

            }
        }

		/*
		 * ALL OTHER COMMANDS WILL FALL UNDER HERE. Checked at calltime for whether or not the one issuing the command is a mod or not.
		 */
		if(message.trim().equalsIgnoreCase("exclamation point dance") || message.trim().equalsIgnoreCase("exclamationpoint dance"))
        {

            //How to send a whisper from NOT the unKnown event. This is essentially raw text sent to the server,
            //So it needs the IRC command, the channel, and the full message
            event.getBot().sendRaw().rawLine("PRIVMSG #tritemare :/w " + sender + " You think you're so funny! Kappa Kappa Kappa Kappa Kappa Kappa" +
                    " Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa " +
                    "Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa Kappa");


        }
        if(message.startsWith("!")){
            String command = message.split(" ")[0]; //Split on any spaces, take the very first chunk in the array generated. Should be the command issued
            switch (command) {
                case "!meonly":
                    if(isMod == 1){
                        String subName = getSubName.get("baronsheep", conn);
                        event.respond("The result is " + subName);
                    }
                case "!addname":
                    if(isMod == 1){
                        String subToAdd = message.split(" ", 2)[1];
                        addKnightName(event, subToAdd);
                    }break;
                case "!editname":
                    if(isMod == 1){
                        String alteredName = message.split(" ", 2)[1];
                        editKnightName(event, alteredName);
                    }break;
                case "!addrule":
                    if(isMod == 1){
                        String ruleAdded = message.split(" ", 2)[1];
                        rules.add(ruleAdded);
                        event.respond("That rule has been added!");
                    }break;
                case "!removerule":
                    if(isMod == 1){
                        String ruleRemoved = message.split(" ", 2)[1];
                        removeRule(event, ruleRemoved);
                    }break;
                case "!listrules":
                    if(isMod == 1){
                        listRules(event);
                    }
                    break;
                case "!clearrules":
                    if(isMod == 1){
                        rules.clear();
                        event.respond("The rules have been erased! Let's get some new ones going!");
                    }break;
                case "!name":
                    if(isMod == 1){ //If a mod, essentially ignore the cooldown and just get the name and return it
                        getKnightName(message.split(" ", 2)[1], event);
                    }else if(isMod == 0 && elapsed > 25 && cmdUse(sender, isMod, whispEna) == 42){ //If not a mod, run them against the command use function. If they're not in the list, let them use it
                        getKnightName(message.split(" ", 2)[1], event);
                    }else if(isMod == 0 && elapsed < 25){ //If the command is on it's personal cooldown and not an individuals cooldown, whisper to the person that the command is on cooldown
                        event.respondWith("/w " + sender + " The name commands are currently on cooldown, it'll be over soon <3");
                    }break;
                case "!myname":
                    if(elapsed > 24 && cmdUse(sender, isMod, whispEna) == 42){
                        getKnightName(sender, event);

                    }else if(elapsed < 25 && isMod == 0){
                        event.respondWith("/w " + sender + " The name commands are currently on cooldown, it'll be over soon <3");
                    }else if(isMod == 1){
                        getKnightName(sender, event);
                    }break;
                case "!dankmemescantmeltsteeldreams":
                    if(elapsed > 25 || isMod == 1){
                        event.respondWith("FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan "
                                + "FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan PRChase FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan "
                                + "FeelsBadMan FeelsBadMan PRChase FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan PRChase FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan FeelsBadMan");}
                    break;
                case "!dance":
                    System.out.println("Dance command used!");
                    event.respondWith("triteM triteD triteM triteD triteM triteD PRChase triteM triteD triteM triteD triteM triteD "
                            + "triteM triteD triteM triteD");
                    break;
                case "!love":
                    System.out.println("Love command used!");
                    event.respondWith("triteL triteL triteL triteL PRChase triteL triteL triteL triteL triteL "
                            + "triteL triteL triteL triteL triteL");
                    break;
                case "!hype":
                    System.out.println("Hype command used!");
                    event.respondWith("triteH triteH triteH triteH PRChase triteH triteH triteH triteH "
                            + "triteH triteH triteH triteH triteH triteH");
                    break;
                case "!woo":
                    System.out.println("Woo Command used!");
                    event.respondWith("triteY triteY triteY triteY triteY PRChase triteY triteY triteY triteY triteY "
                            + "triteY triteY triteY triteY");
                    break;
                case "!dank":
                    if(sender.equalsIgnoreCase("tw0starcore") || sender.equalsIgnoreCase("jaxxx_ol")){
                        event.respondWith("FeelsBadMan");
                    }
                    break;
                case "!rip":
                    System.out.println("rip Command used!");
                    event.respondWith("triteO triteO triteO triteO triteO triteO triteO triteO triteO triteO "
                            + "triteO triteO triteO triteO triteO PRChase");
                    break;
                default:
                    //Do nothing
                    break;
            }
            System.out.println();
        }
    }//end of Message Event Handling

    /**
     * Adds a username+subname to the Map containing all sub names.
     *
     * @param event MessageEvent used to respond back
     * @param nameToAdd username+subname combo to add to list
     */
    public void addKnightName(GenericMessageEvent event, String nameToAdd) {


        String user = nameToAdd.split("=")[0].toLowerCase().trim();
        System.out.println("*" + user + "*");
        String subname = nameToAdd.split("=")[1].replaceAll("'", "").replaceAll("\"", "").trim();

        try {
            String doesExist = getSubName.get(user, conn);
            if (doesExist.equalsIgnoreCase("ERROR")) {
                Boolean success = addSubname.add(user, subname);
                if (success) {
                    event.respondWith("That name has now been recorded! Use !myname to view your knightly name, or " +
                            "!name <user> to see someone else's.");
                } else if (!success){
                    event.respondWith("Something prevented that name from adding to the list, please let JaxXx_oL know!");
                }
            } else{
                event.respondWith("Couldn't add that name to the records triteO, please check the formatting and try again (Do they already have a name?). If it still cant be entered, let JaxXx know!");
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
    }

    /**
     * Takes a username+subname combo that already exists in order to alter to a new version. If the username doesn't exist, wastes time but no problems occur.
     * Responds with a corresponding message
     *
     * @param event MessageEvent used to respond back
     * @param nameToEdit username+sub name to alter
     */
    public void editKnightName(GenericMessageEvent event, String nameToEdit){
        String userName = nameToEdit.split("=")[0].toLowerCase().trim();
        String knightName = nameToEdit.split("=")[1].trim();
        try{
            if((editSubName.edit(userName, knightName, conn))){
                event.respondWith("That name has now been edited!");
            }else{
                event.respondWith("There is no user by that name currently recorded, did you enter it correctly?");
            }
        }catch (Exception e){

        }
    }

    /**
     * Uses the supplied username to read through the list of stored names. If found, responds to the channel with that users Sub-Name,
     * otherwise responds with an error message
     *
     * @param username - Name of the user to search for
     * @param event - Event to respond to
     * S
     * @throws SQLException
     */
    public void getKnightName(String username, GenericMessageEvent event)
    {

        try{
            String subname = getSubName.get(username, conn);
            if(subname.equalsIgnoreCase("ERROR")){
                event.respondWith("There was no knightly name found for " + username +"! "
                        + "If they have been knighted, please alert a mod with their knightly name to get it added to the list! triteH");
            }else{
                event.respondWith(username + "'s knightly name is " + subname);
            }

        }catch (SQLException e){

        }
    }//end of getName function

    /**
     * Writes out changes to the name list and timestamp file if a change has been queued up (mainly timestamp now
     * as the name list has been migrated to a database)
     *
     */
    @Deprecated
    public void cacheOut(){
        /*Reset watcher
        writeNeeded = 0;

        SheetReader.GoogleSheetsReader();

        String timestampList = "./timestamps.txt";
        try(PrintWriter file = new PrintWriter(new BufferedWriter(new FileWriter(timestampList, true))))
        {
            for(String stamp : timestamps){
                file.println(stamp);
            }
            timestamps.clear();
            file.close();
        }catch(IOException e){
            System.out.println(e.getMessage());
        }
        System.out.println("Cached out");

         */
    }

    /**
     * Lists all current rules
     *
     * @param event MessageEvent used to respond back
     */
    public void listRules(GenericMessageEvent event){
        if(rules.isEmpty()){
            event.respondWith("I'm sorry, there needs to be rules in order for me to list them. Add some first!");
        }else{
            for(int i =0; i<rules.size(); i++){
                event.respondWith("Rule " + (i+1) + ": "+ rules.get(i));
            }
        }
    }

    /**
     * Adds a rule to the current list
     *
     * @param event MessageEvent used to respond back
     * @param rule Rule text to add
     */
    public void addRule(GenericMessageEvent event, String rule){
        rules.add(rule);
        event.respond("That rule has been added!");
    }

    /**
     * Removes a rule from the list
     *
     * @param event Used to respond back
     * @param rulePos Position of the rule in the list (1, 2, 3, etc)
     */
    public void removeRule(GenericMessageEvent event, String rulePos){
        int x = (Integer.parseInt(rulePos)) - 1;
        if(rules.size()>=x+1){
            rules.remove(x);
            event.respond("The rule in position " + rulePos + " was removed!");
        }
    }

    /**
     * Connects to twitch API to return the video ID of the most recent broadcast (current ID if streamer is live)
     *
     *
     * @throws IOException - In the event the url cannot be reached
     * @return String of the video id
     */
    private String newStamp() {

        //NEW EASIER WAY
        String videoID="";
        HttpURLConnection conn = null;
        try{
            URL url = new URL("https://api.twitch.tv/helix/videos?user_id=64207063&first=1");//trites user_id on the twitch API, first=1 means only grab the first 1 video, defaults to 20
            conn=(HttpURLConnection)url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Client-ID", clientID);
            conn.setRequestProperty("Authorization", "OAuth " + Api0Auth);

            if(conn.getResponseCode() == 200) {
                JsonParser parser = new JsonParser();
                JsonArray data = parser.parse(new InputStreamReader((InputStream) conn.getContent()))
                        .getAsJsonObject().getAsJsonArray("data");
                // loops through returned objects, the normal api call returns 5 results, however i chose to limit mine to just 1
                // as the first result is the current/most recent video
                for (JsonElement result : data) {
                    videoID = result.getAsJsonObject().get("id").toString();
                    System.out.println("\n\n"+videoID);
                    videoID= videoID.replace("\"","");
                    System.out.println("\n\n"+videoID);
                }
            }else if(conn.getResponseCode() != 200) {
                videoID = "OHSHITNO";
            }



        }catch (Exception e){
            e.printStackTrace();
        }finally{
            conn.disconnect();
        }

        return videoID;


    }//end of Newstamp function

    /**
     * Checks if a user has used a command recently, if not, adds them to a list that is cleared every 90 seconds, if so, disallows them from using the command and adds a message to a queue to be whispered out
     *
     * @param username - Name of the given user to check against
     * @param isMod - integer value for mod status (a 0 or 1)
     * @param event - Message Event just being generalized in order to send easier
     * @return An integer representing whether they were already within the list or not (if not modded. if a mod, never inserts them to bypass)
     * @throws InterruptedException - Potentially may get interrupted
     */
    public int cmdUse(String username, int isMod, Event event) throws InterruptedException {
        username = username.toLowerCase(); // just in case it didn't actually get set to lower outside
        MessageEvent newev = (MessageEvent) event;
        for (String entries: commandUse){
            if(entries.equalsIgnoreCase(username)){
                //newev.respond("PRIVMSG #tritemare :/w " + username + " You are currently on cooldown for the commands, it'll be over soon <3");
                messageQueue.put("PRIVMSG #tritemare :/w " + username + " You are currently on cooldown for the commands, it'll be over soon <3");
                eventQueue.put(newev);

                return 19; // If 19 is returned, it's found that person in the list and basically ignores their request.
            }

        }
        //Only gets this far if the user was not found in the list.
        if(isMod == 1){return 42;}//Don't want to add them if they're a mod, so just returning the number of not found
        else{
            commandUse.add(username);
            //If it returns 42, it added their name to the list and continues on with their request for !name or !myname
            return 42;
        }
    }//end of command use function

    /**
     * Determines if a given user is a moderator of the channel the bot is currently in
     *
     * @param usertype IRC3V tags received as part of the received message
     * @return integer representing modship (0 or 1)
     */
    public int isMod(String usertype){
        //System.out.println(usertype);
        String usertypeArr[] = usertype.split("=");

        int ismod = usertypeArr.length; // If 1, they have no tag, therefor not mod. If they do have a tag, they're something else, assumed mod or higher power

        if(ismod == 2){//There was something on the right side of the =, so either mod/owner?
            return 1;
        }
        else if(ismod == 1){//The other side of the = was null, regular user.
            return 0;
        }
        else{// too many =, ignoring this user, treating them like a regular user.
            return 0;
        }
    }//End of isMod function

    /**
     * Checks if the user is a current subscriber to the channel
     * -- Currently not implemented
     * @param username - Name of the user to check for
     * @param event - Event used to send a response to the channel7
     */
    public void isSub(String username, MessageEvent event){
        //TODO Code to poke the api with a given username to test if they are a sub or not
        //Correction, don't even need to poke the api, it's part of the IRC tags if they're in chat
    }

}