package com.aurora.iunivoice.activity.account;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aurora.iunivoice.R;
import com.aurora.iunivoice.activity.account.VerifyCodeLoader.VC_EVENT;
import com.aurora.iunivoice.utils.Log;
import com.aurora.iunivoice.utils.SystemUtils;
import com.aurora.iunivoice.utils.ToastUtil;
import com.aurora.iunivoice.utils.ValidateUtil;
import com.aurora.iunivoice.widget.ClearableEditText;
import com.aurora.iunivoice.widget.VerifyCodeView;
import com.aurora.datauiapi.data.AccountManager;
import com.aurora.datauiapi.data.bean.BaseResponseObject;
import com.aurora.datauiapi.data.bean.UserVC;
import com.aurora.datauiapi.data.implement.DataResponse;

public class RegisterByPhoneNumActivity extends BaseAccountActivity implements OnClickListener {

	private final int REQUEST_COUNTRY_CODE = 10;
	
	private LinearLayout ll_countryCode;
	private TextView tv_countryCode;
	private ClearableEditText cet_phoneNum;
	private ClearableEditText cet_password;
//	private Button btn_next;
	private VerifyCodeView mVCIv;
	private ClearableEditText mVCCodeEt;
	
	private AccountManager mAccountManager;
	
//	private CountryCode countryCode;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_register_by_phonenum);
		
		setupViews();

		mAccountManager = new AccountManager(this);
		
		// 弹出软键盘
		//CommonUtil.showSoftInputDelay(this, cet_phoneNum, 100);
	}
	
	@Override
	public void setupViews() {
		// TODO Auto-generated method stub
		initViews();
		initData();
//		configActionBar(getString(R.string.register_next));
		setListeners();
	}
	
	@Override
	public void setupAuroraActionBar() {
		super.setupAuroraActionBar();
		setTitleRes(R.string.register_by_phonenum_title);
		
		addActionBarItem(getString(R.string.register_next), ACTION_BAR_RIGHT_ITEM_ID);
	}
	
	@Override
	protected void onActionBarItemClick(View view, int itemId) {
		super.onActionBarItemClick(view, itemId);
		switch (itemId) {
		case BACK_ITEM_ID:
			finish();
			break;
		case ACTION_BAR_RIGHT_ITEM_ID:
			doNext();
			break;
		}
	}

//	@Override
//	protected String getActionBarTitle() {
//		return getString(R.string.register_by_phonenum_title);
//	}

	private void initViews() {
		ll_countryCode = (LinearLayout) findViewById(R.id.ll_countryCode);
		tv_countryCode = (TextView) findViewById(R.id.tv_countryCode);
		cet_phoneNum = (ClearableEditText) findViewById(R.id.cet_phoneNum);
		cet_password = (ClearableEditText) findViewById(R.id.cet_password);
//		btn_next = (Button) findViewById(R.id.btn_next);
		mVCIv = (VerifyCodeView) findViewById(R.id.vc_code_iv);
	    mVCIv.setVCEvent(VC_EVENT.VC_EVENT_REGISTER);
	    mVCCodeEt = (ClearableEditText) findViewById(R.id.vc_code_cet);
	    
		initErrorViews();
	}

	private void initData() {
		// 默认编码
//		countryCode = CommonUtil.getDefaultCountryCode(this);
//		
//		showCountryCode();
	}
	
	private void setListeners() {
		ll_countryCode.setOnClickListener(this);
//		btn_next.setOnClickListener(this);
		setListenerForErrorView(cet_phoneNum);
		setListenerForErrorView(cet_password);
		setListenerForErrorView(mVCCodeEt);
		if (mActionBarRightTv != null) {
		    mActionBarRightTv.setOnClickListener(this);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
//		case R.id.ll_countryCode:
//			Intent codeIntent = new Intent(RegisterByPhoneNumActivity.this, CountryCodeActivity.class);
//			startActivityForResult(codeIntent, REQUEST_COUNTRY_CODE);
//			break;
//		case R.id.btn_next:
//			doNext();
//		case R.id.right_tv:
//			doNext();
//			break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
//		if (requestCode == REQUEST_COUNTRY_CODE && resultCode == RESULT_OK) {
//			if (data != null) {
//				CountryCode countryCode = data.getParcelableExtra(CountryCodeActivity.COUNTRY_CODE);
//				if (countryCode != null) {
//					this.countryCode = countryCode;
//					showCountryCode();
//				}
//			}
//		}
	}
	
//	private void showCountryCode() {
//		if (countryCode != null) {
//			tv_countryCode.setText(countryCode.getCountryOrRegionsCN() +
//					addPlusToCode(countryCode.getCode()));
//		}
//	}
	
	private String addPlusToCode(String code) {
		try {
			int codeInt = Integer.parseInt(code);
			if (codeInt > 0) {
				return "+" + codeInt;
			}
		} catch (NumberFormatException e) {
		}
		
		return code;
	}
	
	private void doNext() {
		String phoneNum = cet_phoneNum.getText().toString().trim();
		String pwd = cet_password.getText().toString().trim();
		String validCode = mVCCodeEt.getText().toString().trim();
		
		if (!ValidateUtil.isMobilePhoneNumVaild(phoneNum) || TextUtils.isEmpty(phoneNum)) {
		    showErrorInfo(findViewById(R.id.cet_phoneNum_ly), cet_phoneNum, getResources().getString(R.string.register_error_phonenum_format_invalid));
            return;
        }
		
		if (TextUtils.isEmpty(pwd) || pwd.length() < getResInteger(R.integer.password_min_length)) {
		    showErrorInfo(findViewById(R.id.cet_password_ly), cet_password, getResources().getString(R.string.register_error_pwd_cannot_less_than_8));
			return;
		}
		
		if (!ValidateUtil.isCorrectFormatPwd(pwd)) {
		    showErrorInfo(findViewById(R.id.cet_password_ly), cet_password, getResources().getString(R.string.register_error_pwd_format_invalid));
            return;
        }
		
		if (TextUtils.isEmpty(validCode)) {
			showErrorInfo(findViewById(R.id.vc_code_ly), mVCCodeEt, getResources().getString(R.string.change_pwd_error_vccode_cannot_be_empty));
			return;
		}
		
		getNetData();
	}
	
	private void getNetData() {
		final String phoneNum = cet_phoneNum.getText().toString().trim();
		final String validCode = mVCCodeEt.getText().toString().trim();
		
		long num_vercode_last_time = mPref.getVercodeLastSendTime(phoneNum);
		
		if (System.currentTimeMillis() - num_vercode_last_time < 
				PhoneNumVerificationActivity.CODE_REGET_TIME * 1000) {
			String pwd = cet_password.getText().toString().trim();
			Intent nextIntnet = new Intent(RegisterByPhoneNumActivity.this, PhoneNumVerificationActivity.class);
			nextIntnet.putExtra(PhoneNumVerificationActivity.PHONE_NUM, phoneNum);
			nextIntnet.putExtra(PhoneNumVerificationActivity.PWD, pwd);
//			nextIntnet.putExtra(PhoneNumVerificationActivity.COUNTRY_CODE, countryCode.getCode());
			startActivity(nextIntnet);
			return;
		}
		
		showProgressDialog(getString(R.string.register_getting_verify_code));
		
		mAccountManager.getVerifyCode(new DataResponse<UserVC>() {
			public void run() {
				if (value != null) {
					Log.i(TAG, "the value=" + value.getCode());
					
					dismissProgressDialog();
					
					// 是否需要刷新图片验证码
					boolean needRefresh = true;
					
					if (value.getCode() == BaseResponseObject.CODE_SUCCESS) {
					    mPref.recordLastSendTime(phoneNum);
					    mPref.recordLastSendTimeMode(phoneNum, PhoneNumVerificationActivity.MODE_FOR_REGISTER);
						
						String pwd = cet_password.getText().toString().trim();
						Intent nextIntnet = new Intent(RegisterByPhoneNumActivity.this, PhoneNumVerificationActivity.class);
						nextIntnet.putExtra(PhoneNumVerificationActivity.PHONE_NUM, phoneNum);
						nextIntnet.putExtra(PhoneNumVerificationActivity.PWD, pwd);
//						nextIntnet.putExtra(PhoneNumVerificationActivity.COUNTRY_CODE, countryCode.getCode());
						nextIntnet.putExtra(PhoneNumVerificationActivity.VALIDCODE, validCode);
						startActivity(nextIntnet);
						
						needRefresh = false;
					} else if (value.getCode() == UserVC.CODE_ERROR_PHONE_NUM_ALREADY_REGISTERED) {
						ToastUtil.shortToast(getString(R.string.validate_pwd_error_phonenum_already_registered));
					} else if (value.getCode() == UserVC.CODE_ERROR_PHONE_NUM_NOT_REGISTERED) {
						ToastUtil.shortToast(getString(R.string.validate_pwd_error_phonenum_not_registered));
					} else if (value.getCode() == UserVC.CODE_ERROR_FAILED_TO_SEND) {
						ToastUtil.shortToast(getString(R.string.validate_pwd_error_failed_to_send));
					} else if (value.getCode() == UserVC.CODE_ERROR_SEND_MSG_FREQUENT) {
						ToastUtil.shortToast(getString(R.string.register_code_error_send_msg_frequent));
					} else if (value.getCode() == UserVC.CODE_ERROR_CHECK_INPUT_ERROR) {
						ToastUtil.shortToast(getString(R.string.findpwd_error_checkcode_input_error));
					} else {
						ToastUtil.shortToast(getString(R.string.register_get_verify_code_fail));
					}
					
					if (needRefresh) {
						// 刷新界面验证码
						if (mVCIv != null) {
							mVCIv.refresh();
						}
					}
				}
			}
		}, RegisterByPhoneNumActivity.this, phoneNum, "register", validCode, SystemUtils.getIMEI());
	}

}
