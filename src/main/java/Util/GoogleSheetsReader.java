package Util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.pircbotx.hooks.events.MessageEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class GoogleSheetsReader {

    private static final String APPLICATION_NAME = "Google Sheets API";

    /** Directory to store user credentials for this application. */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.home"), ".credentials/sheets.googleapis.com-java-quickstart");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY =
            JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/sheets.googleapis.com-java-quickstart
     */
    private static final List<String> SCOPES =
            Arrays.asList(SheetsScopes.SPREADSHEETS);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }


    public static void GoogleSheetsReader(MessageEvent event, String user) throws IOException {
        // Build a new authorized API client service.
        Sheets service = getSheetsService();

        // List of keys for giveaway
        //COL A: Beta keys
        //COL B: Flare Keys
        //COL C: Name of Winner
        //COL D: Are they following
        //COL E: DONE? (I guess so that we know it was handed out?)
        // https://docs.google.com/spreadsheets/d/1VFANNya37lbImW4YtGAYeVJm0OipYe_62PsxndhVBZs/edit#gid=0
        String spreadsheetId = "1VFANNya37lbImW4YtGAYeVJm0OipYe_62PsxndhVBZs";
        String range = "Sheet1!A368:C";
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.size() == 0) {
            System.out.println("No data found.");
        } else {
            System.out.println("Beta Key  -- Flare Key -- Username");
            Integer count = 368;
            for (List row : values) {
                // Print columns A Through E, which correspond to indices 0 - 4.

                if(row.size()>1){
                   String winners = row.get(2).toString();
                   if(winners.equalsIgnoreCase(user)){
                       event.respond("That user already received a code!");
                       break;
                   }

                }
                if(row.size()==1) {
                List<List<Object>> inserts = Arrays.asList(
                        Arrays.asList(
                                user,
                                "YES",
                                "DONE",
                                "BOT UPDATED"
                        )

                );
                ValueRange body = new ValueRange()
                        .setValues(inserts);
                UpdateValuesResponse result =
                        service.spreadsheets().values().update(spreadsheetId, "C" + count + ":F" + count, body)
                                .setValueInputOption("RAW")
                                .execute();
                System.out.printf("%d cells updated.", result.getUpdatedCells());
                System.out.println("Row num: " + count + " Row Length: " + row.size() + "  --  " + row.get(0) + " -- Goes to " + user);
                event.respond("Message sent to " + user);
                event.respondWith("/w " + user + " Your game/flare code is: " + row.get(0));
                break; // ONLY UPDATE THE MOST RECENTLY FOUND UNASSIGNED KEY
                }
                count++;
            }
        }
    }




    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in =
                GoogleSheetsReader.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");
        System.out.println(
                "Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Sheets API client service.
     * @return an authorized Sheets API client service
     * @throws IOException
     */
    public static Sheets getSheetsService() throws IOException {
        Credential credential = authorize();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

}
