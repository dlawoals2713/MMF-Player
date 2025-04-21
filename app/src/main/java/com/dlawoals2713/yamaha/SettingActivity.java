package com.dlawoals2713.yamaha;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import de.dlyt.yanndroid.oneui.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import de.dlyt.yanndroid.oneui.layout.ToolbarLayout;
import java.util.ArrayList;
import java.util.List;

import de.dlyt.yanndroid.oneui.dialog.AlertDialog;
import androidx.appcompat.widget.SeslSpinner;

public class SettingActivity extends AppCompatActivity {
private Context mContext;
private ToolbarLayout toolbarLayout;
private SeslSpinner spinner;
private SharedPreferences sp;
private ArrayAdapter<String> adapter;
private List<String> rateList;
private final String PREF_KEY = "sr";
private final String DEFAULT_SR = "22050";

    private void initSpinner() {
        String[] defaultRates = getResources().getStringArray(R.array.sample_rates);
        rateList = new ArrayList<>();

        for (String rate : defaultRates) {
            rateList.add(rate);
        }
        
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rateList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // 저장된 값으로 초기 선택 설정
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
        sp.edit().putString(PREF_KEY, rate).apply();
        Toast.makeText(this, "저장되었습니다. 적용하려면 서비스를 재시작해야 합니다.", Toast.LENGTH_SHORT).show();
    }

    private int getIndexByRate(String rate) {
        for (int i = 0; i < rateList.size(); i++) {
            if (stripHz(rateList.get(i)).equals(rate)) {
                return i;
            }
        }
        return rateList.size() - 1; // 사용자 지정으로 fallback
    }

    private String stripHz(String s) {
        return s.replace(" Hz", "").trim();
    }
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.setting);
		initialize(_savedInstanceState);
		initializeLogic();
	}
	
	private void initialize(Bundle _savedInstanceState) {
	}
	
	private void initializeLogic() {
		toolbarLayout = findViewById(R.id.toolbar_view);
		toolbarLayout.setNavigationButtonOnClickListener(view -> onBackPressed());
		
		spinner = findViewById(R.id.sesl_spinner); // xml에 정의된 ID 사용
		sp = getSharedPreferences("setting", MODE_PRIVATE);
		
		initSpinner();
	}
	
	
	@Deprecated
	public void showMessage(String _s) {
		Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
	}
	
	@Deprecated
	public int getLocationX(View _v) {
		int _location[] = new int[2];
		_v.getLocationInWindow(_location);
		return _location[0];
	}
	
	@Deprecated
	public int getLocationY(View _v) {
		int _location[] = new int[2];
		_v.getLocationInWindow(_location);
		return _location[1];
	}
	
	@Deprecated
	public int getRandom(int _min, int _max) {
		Random random = new Random();
		return random.nextInt(_max - _min + 1) + _min;
	}
	
	@Deprecated
	public ArrayList<Double> getCheckedItemPositionsToArray(ListView _list) {
		ArrayList<Double> _result = new ArrayList<Double>();
		SparseBooleanArray _arr = _list.getCheckedItemPositions();
		for (int _iIdx = 0; _iIdx < _arr.size(); _iIdx++) {
			if (_arr.valueAt(_iIdx))
			_result.add((double)_arr.keyAt(_iIdx));
		}
		return _result;
	}
	
	@Deprecated
	public float getDip(int _input) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _input, getResources().getDisplayMetrics());
	}
	
	@Deprecated
	public int getDisplayWidthPixels() {
		return getResources().getDisplayMetrics().widthPixels;
	}
	
	@Deprecated
	public int getDisplayHeightPixels() {
		return getResources().getDisplayMetrics().heightPixels;
	}
}