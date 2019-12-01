package Listeners;

import Util.SQLTools.GETSubname;
import Util.SQLTools.SQLConnect;
import Util.SQLTools.UPDATEmonths;
import com.google.common.collect.ImmutableSetMultimap;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.UnknownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;


public class TwitchUnknownHandler extends ListenerAdapter{

    static final Logger l = LoggerFactory.getLogger(TwitchUnknownHandler.class);
    private final UPDATEmonths updateMonths = new UPDATEmonths();
    private Connection conn = new SQLConnect().connect();

    @Override
    //UNKNOWN EVENT, basically something other than a event that is known by pircbotx
    public void onUnknown(UnknownEvent event) throws Exception {
        String raw = event.getRawLine();
        l.info(raw);
        //Auto fills the multimap using the function
        ImmutableSetMultimap<String, String> tags = event.getTags().asMultimap();


        System.out.println("THE FOLLOWING ARE ALL TAGS ASSOCIATED WITH THIS UNKNOWN EVENT FIRING");
        System.out.println("******************************************\n");
        for (String key: tags.keys()){

            System.out.println(key.toString() + " -- " + tags.get(key).toArray()[0]);
        }
        System.out.println("\n******************************************\n");

        //CHECK FOR RESUB NOTIFICATION
        if(tags.containsKey("msg-param-cumulative-months")){

            l.info("\nFOUND THIS PARAM");

            String name = tags.get("login").toArray()[0].toString();


            int x = Integer.parseInt(tags.get("msg-param-cumulative-months").toArray()[0].toString());





            //If resubbing
            if(x>1) {
                //Opening up a connection to the database


                String subName = new GETSubname().get(name, conn);

                event.respond("PRIVMSG #tritemare :          Thank you " + subName + " for " + x + " months of love! "
                        + "triteH triteL triteH triteL triteH triteL triteH");

               if(updateMonths.edit(x,name)){
                   System.out.println("Subscriber " +name+ " has had their total months subbed updated ---");
               }else{
                   System.out.println("Error on updating sub entry, does that subscriber actually exist?");
                   event.respond("PRIVMSG #tritemare :          There seems to have been an issue updating the entry for that user...");
               }
            }
            //Subscribing for the first time
            else if(x==1){

                event.respond("PRIVMSG #tritemare :          Thank you " + name + " for subscribing! "
                        + "triteH triteL triteH triteL triteH triteL triteH");
            }
        }


		/* WHISPER HANDLING */
        String line = event.toString();
        if(line.contains("WHISPER"))
        {
            try{

                //System.out.println(line);// Debugging purposes, needed to see what the event looked like fully to be able to parse it
                line = line.split(":", 2)[1]; // removing the first chunk of event text up to the user sending the message
                String sender = line.split("!")[0]; // using the next unique character to grab the user
                String almostSent = line.substring(line.indexOf("WHISPER tritebot :"), line.indexOf("rawLine")); // grabbing the next unique character to grab the message sent to the channel, up to but not including the last character of the event text
                String textSent = almostSent.substring(18, almostSent.length()-2);
                //System.out.println("Text sent to bot in whisper from " + sender + ": " + textSent); // Test output to show text being grabbed fully
                event.respond("PRIVMSG #tritemare :/w " + sender + " You've sent: \"" + textSent + "\" as a message to me. Why would you whisper a bot?");
            } catch(Exception e){
                System.out.println(e.getMessage());
            }
        }
    }

}