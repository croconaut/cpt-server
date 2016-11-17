package com.croconaut.ratemebuddy.data.pojo;

import com.croconaut.ratemebuddy.utils.XorString;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Base64;


public class Message implements Serializable {
    private static final long serialVersionUID = 55L;

    private final String content;
    private final String profileId;
    private final String profileName;

    public Message(final String content, final String profileId, String profileName){
        this.profileId = profileId;
        this.profileName = profileName;

        String text = content;
        try {
            //int flags = Base64.NO_WRAP | Base64.NO_PADDING;
            //String textB64 = Base64.encodeToString(text.getBytes(), flags);
            String textB64 = Base64.getEncoder().withoutPadding().encodeToString(text.getBytes("utf-8"));
            String passB64 = "XiB1cDNyLVQ0am7DqS5IM3NsMCxQcmUvS8OzRG92NG5pZSpTcHLDoXYr"; //Base64.encodeToString(pass.getBytes(), Base64.DEFAULT);
            XorString xs = new XorString();
            text = xs.xoring(textB64, passB64);
        } catch (UnsupportedEncodingException e) {
            // nothing to do
            e.printStackTrace();
        } finally {
            this.content = text;
        }
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getContent() {
        return content;
    }
}
