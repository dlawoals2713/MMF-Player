package com.dlawoals2713.yamaha;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import de.dlyt.yanndroid.oneui.layout.ToolbarLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import androidx.appcompat.widget.SeslSpinner;

public class SettingActivity extends AppCompatActivity {
    private SeslSpinner spinner;
    private SharedPreferences sp;
    private List<String> rateList;
    private final String PREF_KEY = "sr";
    private final String DEFAULT_SR = "22050";

    private void initSpinner() {
        String[] defaultRates = getResources().getStringArray(R.array.sample_rates);
        rateList = new ArrayList<>();

        Collections.addAll(rateList, defaultRates);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rateList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String savedRate = sp.getString(PREF_KEY, DEFAULT_SR);
        int selectedIndex = getIndexByRate(savedRate);
        spinner.setSelection(selectedIndex);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = rateList.get(position);
                saveRate(stripHz(selected));
            }
        
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 아무 것도 선택 안 했을 때 처리할 게 있으면 여기에
            }
        });
    }

    private void saveRate(String rate) {
        if (!Objects.equals(rate, sp.getString(PREF_KEY, DEFAULT_SR))) {
            sp.edit().putString(PREF_KEY, rate).apply();
            Toast.makeText(this, getString(R.string.setting_saved_rate), Toast.LENGTH_SHORT).show();
        }
    }

    private int getIndexByRate(String rate) {
        for (int i = 0; i < rateList.size(); i++) {
            if (stripHz(rateList.get(i)).equals(rate)) {
                return i;
            }
        }
        return rateList.size() - 1;
    }

    private String stripHz(String s) {
        return s.replace(" Hz", "").trim();
    }
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.setting);
		initializeLogic();
	}
	
	private void initializeLogic() {
        ToolbarLayout toolbarLayout = findViewById(R.id.toolbar_view);
        toolbarLayout.setNavigationButtonOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());

        spinner = findViewById(R.id.sesl_spinner);
		sp = getSharedPreferences("setting", MODE_PRIVATE);
		
		initSpinner();
	}
	
	
	@Deprecated
	public void showMessage(String _s) {
		Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
	}
}