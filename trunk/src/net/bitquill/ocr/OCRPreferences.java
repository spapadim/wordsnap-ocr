package net.bitquill.ocr;

import net.bitquill.ocr.weocr.WeOCRServerList;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.ListAdapter;

public class OCRPreferences extends PreferenceActivity 
implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    
    public static final String PREF_WEOCR_ENDPOINT = "weocr_endpoint_url";
    
    private static final int ID_WEOCR_SERVERS_DIALOG = 1;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load XML preferences file
        addPreferencesFromResource(R.xml.ocr_prefs);
        
        Preference p = findPreference(PREF_WEOCR_ENDPOINT);
        p.setOnPreferenceClickListener(this);
        p.setOnPreferenceChangeListener(this);        
    }
    
    @Override
    protected Dialog onCreateDialog (int id) {
        switch(id) {
        case ID_WEOCR_SERVERS_DIALOG:
            final ListAdapter serversAdapter = OCRApplication.getOCRServerList().getServerListAdapter(this);
            return new AlertDialog.Builder(this)
                .setTitle(R.string.servers_dialog_title)
                .setAdapter(serversAdapter,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                OCRApplication.getInstance().rebindServer(((WeOCRServerList.Server)serversAdapter.getItem(which)).endpoint);
                                // FIXME FIXME FIXME !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                                OCRPreferences.this.dismissDialog(ID_WEOCR_SERVERS_DIALOG);
                            }
                        })
                .setNeutralButton(R.string.close_button, null)
                .create();
        default:
            return super.onCreateDialog(id);
        }
    }
    
    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (PREF_WEOCR_ENDPOINT.equals(pref.getKey())) {
            //pref.setSummary((String)newValue);
            // TODO
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (PREF_WEOCR_ENDPOINT.equals(pref.getKey())) {
            showDialog(ID_WEOCR_SERVERS_DIALOG);
        }
        return false;
    }

}
