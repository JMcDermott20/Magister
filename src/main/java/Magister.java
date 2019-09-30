import Util.GetLogin0Auth;
import Util.SQLConnect;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.EnableCapHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class Magister {
    private static String oauth = null;
    private static final Logger log = LoggerFactory.getLogger(Magister.class);


    public static void main(String[] args) throws SQLException {

        try {
            log.info("Connecting to the backend...");
            Connection conn = new SQLConnect().connect();
            log.info("Connected ");

            Thread.sleep(TimeUnit.SECONDS.toMillis(2));

            log.info("Retrieving Twitch 0Auth token from database");
            oauth = new GetLogin0Auth().getAuth(conn);

            Thread.sleep(TimeUnit.SECONDS.toMillis(2));

            if(oauth.startsWith("err")){
                log.error("Failed to retrieve login token from backend, exiting");
                System.exit(0);
            }


            /*
            ResourceBundle bundle = ResourceBundle.getBundle("twitch");
            oauth = bundle.getString("oauth");
            */

        }catch (InterruptedException | SQLException e){
            log.error("Exception thrown in initialization: "+ e.getLocalizedMessage());
            System.exit(0);
        }

        //Setup this bot
        Configuration configuration = makeConf("#tritemare");
        //bot.connect throws various exceptions for failures
        launchBot(configuration);
    }

    /**
     * Launches a bot with a given configuration and adds it to the list of currently running bots
     *
     * @param config - the configuration build in which to launch the bot with
     */
    public static void launchBot(Configuration config){
        try {
            log.info("Launching bot and connecting to channel #tritemare");
            PircBotX bot = new PircBotX(config);

            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
            //Connect to the Twitch network
            bot.startBot();
        } //In your code you should catch and handle each exception separately,
        //but here we just lump them all together for simplicity
        catch (Exception ex) {
            log.error(ex.getStackTrace().toString());
        }
    }
    /*
     * @param channel - the channel in which to join using this configuration
     */

    /**
     * Generates the configuration needed to launch a bot
     *
     * @param channel - the channel in which to set the bot to join
     * @return Configuration - a built configuration ready to sent off to launch a bot with
     */
    public static  Configuration makeConf(String channel) throws SQLException {
        log.info("Configuring Bot for Tritemare's Twitch Channel");
        Configuration configuration = new Configuration.Builder()
                .setCapEnabled(true) //Enable CAP features
                .addCapHandler(new EnableCapHandler("twitch.tv/membership")) //PART, JOIN, NAMES
                .addCapHandler(new EnableCapHandler("twitch.tv/tags"))
                .addCapHandler(new EnableCapHandler("twitch.tv/commands"))
                .setAutoNickChange(false)
                .setAutoReconnect(true)
                .setOnJoinWhoEnabled(false)

                //Account details for logging into Twitch
                .addServer("irc.chat.twitch.tv") //General Chat
                .addServer("199.9.253.119", 6667) //Whisper Chat
                .setName("tritebot") //Nick/Login/Username of bot
                .setServerPassword(oauth) //0Auth token
                .addAutoJoinChannel(channel)

                //Listeners for commands/logging
                //This class is a listener, so add it to the bots known listeners
                .addListener(new Listeners.TwitchMessageHandler())//Default Listener, message/command handling
                .addListener(new Listeners.TwitchUnknownHandler())//Unknown Event Listener, whispers and USERNOTICE notifications
                .buildConfiguration();
        return configuration;

    }

}
