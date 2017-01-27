package com.croconaut.ratemebuddy.data.pojo;

import com.croconaut.ratemebuddy.utils.XorString;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Base64;


public class Message implements Serializable {
    private static final long serialVersionUID = 55L;

    private static final String passwordB64 = "XiB1cDNyLVQ0am7DqS5IM3NsMCxQcmUvS8OzRG92NG5pZSpTcHLDoXYr";

    private final String content;
    private final String profileId;
    private final String profileName;

    public Message(final String content, final String profileId, String profileName) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.content = content;
    }

    public Message encoded() {
        String text = content;
        try {
            String textB64 = Base64.getEncoder().withoutPadding().encodeToString(text.getBytes("utf-8"));
            XorString xs = new XorString();
            text = xs.xoring(textB64, passwordB64);
        } catch (UnsupportedEncodingException e) {
            // nothing to do
            e.printStackTrace();
        }
        return new Message(text, profileId, profileName);
    }

    public Message decoded() {
        String text = content;
        try {
            XorString xs = new XorString();
            String textB64 = xs.xoring(content, passwordB64);
            text = new String(Base64.getDecoder().decode(textB64), "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return new Message(text, profileId, profileName);
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
