package net.bitquill.ocr.weocr;

import java.io.IOException;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.XmlResourceParser;

public class WeOCRServerList {
    
    private ArrayList<Server> mServerList;
    
    public static class Server {
        public String title;
        public String organization;
        public String url;
        public String endpoint;
        public String engine;
        public long mtime;
        public String[] languages;
        
        public Server () { }
        
        public void setLanguages (String langcodes) {
            languages = langcodes.split(":");
        }
        
        public boolean supportsLanguage (String langcode) {
            if (languages == null) {
                return false;
            }
            for (String l : languages) {
                if (l.equals(langcode)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    private static final String SERVER_TAG = "server";
    private static final String TITLE_TAG = "title";
    private static final String ORGANIZATION_TAG = "organization";
    private static final String URL_TAG = "url";
    private static final String ENDPOINT_TAG = "cgi";
    private static final String ENGINE_TAG = "engine";
    private static final String MTIME_TAG = "mtime";
    private static final String LANGCODES_TAG = "langcodes";
    
    public WeOCRServerList (Context context, int xmlResId) throws IOException, XmlPullParserException {
        mServerList = new ArrayList<Server>();
        XmlResourceParser parser = context.getResources().getXml(xmlResId);
        Server srv = null;
        for (int eventType = parser.getEventType(); 
                 eventType != XmlPullParser.END_DOCUMENT; 
                 eventType = parser.next()) {
            String tagName = null;
            switch (eventType) {
            case XmlPullParser.START_TAG:
                tagName = parser.getName();
                if (SERVER_TAG.equals(tagName)) {
                    if (srv != null) {
                        throw new XmlPullParserException("Unexpected start of server element");
                    }
                    srv = new Server();
                } else if (TITLE_TAG.equals(tagName)) {
                    srv.title = parser.nextText();
                } else if (ORGANIZATION_TAG.equals(tagName)) {
                    srv.organization = parser.nextText();
                } else if (URL_TAG.equals(tagName)) {
                    srv.url = parser.nextText();
                } else if (ENDPOINT_TAG.equals(tagName)) {
                    srv.endpoint = parser.nextText();
                } else if (ENGINE_TAG.equals(tagName)) {
                    srv.engine = parser.nextText();
                } else if (MTIME_TAG.equals(tagName)) {
                    srv.mtime = Long.parseLong(parser.nextText());
                } else if (LANGCODES_TAG.equals(tagName)) {
                    srv.setLanguages(parser.nextText());
                }
                break;
            case XmlPullParser.END_TAG:
                tagName = parser.getName();
                if (SERVER_TAG.equals(tagName)) {
                    mServerList.add(srv);
                    srv = null;
                }
                break;
            }
        }
        parser.close();
    }
}
