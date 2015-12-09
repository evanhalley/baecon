package com.emuneee.baecon;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by evanhalley on 11/13/15.
 */
public class Baecon {

    private String username;
    private String message;

    public Baecon(String username, String message) {
        this.username = username;
        this.message = message;
    }

    public Baecon(String jsonStr) {

        try {
            JSONObject json = new JSONObject(jsonStr);
            username = json.getString("username");
            message = json.getString("message");
        } catch (JSONException e) {
            throw new IllegalStateException("Unparsable baecon");
        }
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return message;
    }

    public String toJson() {

        try {
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("message", message);
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("");
        }
    }
}
