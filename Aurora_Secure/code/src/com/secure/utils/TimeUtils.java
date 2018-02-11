package com.secure.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.text.format.DateFormat;

public class TimeUtils {
	/**
	 * @param x
	 * @return
	 */
	public static String TimeFormat(int x) { 
	    String s=""+x; 
	    if(s.length()==1) s="0"+s; 
	    return s; 
	} 
	
	/**
	 * @param time
	 * @return
	 */
	public static String SecondToHHMMSS(Long time){	
		int hours = (int)(time/60/60);
		int mins = (int)(time/60)-hours*60;
		int seconds = (int)(time-hours*60*60-mins*60);
		StringBuilder timeStr = new StringBuilder().
				append(TimeFormat(hours)).
				append(":") .
				append(TimeFormat(mins)).
				append(":").
				append(TimeFormat(seconds));
		return timeStr.toString();
	}
	
	/**
	 * @param time
	 * @return
	 */
	public static String SecondToHHMM(Long time){	
		int hours = (int)(time/60/60);
		int mins = (int)(time/60)-hours*60;
		StringBuilder timeStr = new StringBuilder().
				append(TimeFormat(hours)).
				append(":") .
				append(TimeFormat(mins));
		return timeStr.toString();
	}
	
	/**
	 * @param time
	 * @return ֻ
	 */
	public static int SecondToHH(Long time){	
		int hours = (int)(time/60/60);
		return hours;
	}
	
	/**
	 * @param time
	 * @return ֻ
	 */
	public static int SecondToMM(Long time){	
		int hours = (int)(time/60/60);
		int mins = (int)(time/60)-hours*60;
		return mins;
	}
	
	/**
	 * @param time
	 * @return ֻ
	 */
	public static int SecondToSS(Long time){	
		int hours = (int)(time/60/60);
		int mins = (int)(time/60)-hours*60;
		int seconds = (int)(time-hours*60*60-mins*60);
		return seconds;
	}
	
	/**
	 * @return
	 */
	public static synchronized String getCurTime(){
		String curTime = new DateFormat().format("yyyyMMddhhmmss",
				Calendar.getInstance(Locale.CHINA)).toString();
		return curTime;
	}
	
    /**
     * 获取当前时间戳
     * @return
     */
    public static synchronized long getCurrentTimeMillis(){
		Calendar cal = Calendar.getInstance();       
        return cal.getTimeInMillis();//该值与System.currentTimeMillis()获取的值相同
    }
	
	/**
	 * 将时间戳转化成日期格式
	 * @param time
	 * @return
	 */
	public static String timeStampToData(long time){
		SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");  
		return sdf.format(new Date(time));  
	}
	
	/**
     * 计算两个日期间相差的天数
     * @param date1
     * @param date2
     * @return 如果date1（2005-01-01）晚于date2（2003-01-01），返回正数天数
     *         如果date1（2003-01-01）早于date2（2005-01-01），返回负数天数
     */
    public static int calculationDaysBetweenTwodate(String date1,String date2){
    	int day = 0;
    	SimpleDateFormat sdf=new SimpleDateFormat("yyyy-mm-dd");
    	try {
	    	long l_date1 = sdf.parse(date1).getTime();
	    	long l_date2=sdf.parse(date2).getTime();
	    	day=(int)((l_date1-l_date2)/(1000*60*60*24));
		} catch (ParseException e) {
			e.printStackTrace();
		}
    	return day;
    }
    
    /**
     * 获取距离月结日剩余的天数
     * @param monthEndDate 月结日
     * @return
     */
    public static int getRemainderDaysToMonthEndDate(final int monthEndDate){	
    	int cur_day,cur_month,cur_year;//当前日，月，年
    	int end_day,end_month,end_year;//月结日，月，年
    	
    	Calendar cal = Calendar.getInstance();
    	cur_day = cal.get(Calendar.DATE);
        cur_month = cal.get(Calendar.MONTH)+1;//月份的起始值为０而不是１，所以要设置八月时，我们用７而不是8。
        cur_year = cal.get(Calendar.YEAR);
        
        //确定月结月，月结年
        if(cur_day >= monthEndDate){
        	end_month = cur_month+1;
        	if(end_month > 12){
        		end_month = 1;
        		end_year = cur_year+1;
        	}else{
        		end_year = cur_year;
        	}
        }else{
        	end_month = cur_month;
        	end_year = cur_year;
        }
               
        //确定月结日
        int end_month_last_day = getMonthLastDay(end_year,end_month);
        if(monthEndDate>end_month_last_day){
        	end_day = end_month_last_day;
        }else{
        	end_day = monthEndDate;
        }
        
        Calendar calendar1 = Calendar.getInstance();
        Calendar calendar2 = Calendar.getInstance();
        calendar1.set(cur_year, cur_month-1, cur_day,0,0,0);//月份的起始值为０而不是１
        calendar2.set(end_year, end_month-1, end_day,0,0,0);//月份的起始值为０而不是１
        long milliseconds1 = calendar1.getTimeInMillis();
        long milliseconds2 = calendar2.getTimeInMillis();
        long diff = milliseconds2 - milliseconds1;
        return (int)(diff/(24*60*60*1000));
    }
    
    /**
     * 获取上一个月结日的时间搓
     * @param monthEndDate 月结日
     * @return
     */
    public static long getLastMonthEndTimeStamp(final int monthEndDate){	
    	int last_end_day,last_end_month,last_end_year;//上一个月结日，月，年
    	int cur_day,cur_month,cur_year;//当前日，月，年
    	
    	Calendar cal = Calendar.getInstance();
    	cur_day = cal.get(Calendar.DATE);
        cur_month = cal.get(Calendar.MONTH)+1;//月份的起始值为０而不是１       
        cur_year = cal.get(Calendar.YEAR);
        
        //确定月结月，月结年
        if(cur_day >= monthEndDate){
        	last_end_month = cur_month;
        	last_end_year = cur_year;
        }else{
        	last_end_month = cur_month-1;
         	if(last_end_month <= 0){
         		last_end_month = 12;
         		last_end_year= cur_year-1;
         	}else{
         		last_end_year = cur_year;
         	}
        }
               
        //确定上一个月结日
        int last_end_month_last_day = getMonthLastDay(last_end_year,last_end_month);
        if(monthEndDate>last_end_month_last_day){
        	last_end_day = last_end_month_last_day;
        }else{
        	last_end_day = monthEndDate;
        }
           
        Calendar calendar1 = Calendar.getInstance();
        calendar1.set(last_end_year, last_end_month-1, last_end_day,0,0,0);//月份的起始值为０而不是１   
        return calendar1.getTimeInMillis();      
    }
    
    /**
     * 得到指定月的天数
     * */
    public static int getMonthLastDay(int year, int month){
	    Calendar a = Calendar.getInstance();	
	    a.set(Calendar.YEAR, year);	
	    a.set(Calendar.MONTH, month-1);//月份的起始值为０而不是１
	    a.set(Calendar.DATE, 1);//把日期设置为当月第一天	
	    a.roll(Calendar.DATE, -1);//日期回滚一天，也就是最后一天	
	    int maxDate = a.get(Calendar.DATE);	
	    return maxDate;
    }
    
    /**
     * 获取今天起始时间戳
     * @param day
     * @return
     */
    public static long getTodayTimeStamp(){
    	Calendar calendar1 = Calendar.getInstance();
    	calendar1.set(Calendar.HOUR_OF_DAY, 0);
    	calendar1.set(Calendar.MINUTE, 0);
    	calendar1.set(Calendar.SECOND, 0);     
        return calendar1.getTimeInMillis(); 
    }
    
    /**
     * 获取当年当月某一天的时间戳
     * @param day
     * @return
     */
    public static long getOneDayTimeStamp(int day){
    	Calendar cal = Calendar.getInstance();
    	int cur_year = cal.get(Calendar.YEAR);
        int cur_month = cal.get(Calendar.MONTH)+1;//月份的起始值为０而不是１       
        int curMonthDays = getMonthLastDay(cur_year,cur_month);
        if(day>curMonthDays){
        	day = curMonthDays;
        }     
        return getOneDayTimeStamp(cur_year,cur_month,day);       
    }
    
    /**
     * 获取某一天的时间戳
     * @param day
     * @return
     */
    public static long getOneDayTimeStamp(int year,int month,int day){         
    	Calendar calendar1 = Calendar.getInstance();
        calendar1.set(year, month-1, day,0,0,0);//月份的起始值为０而不是１       
        return calendar1.getTimeInMillis();
    }
    
    /**
     * 判断指定时间戳是不是在本次月结日内
     * @param monthEndDate 月结日
     * @return
     */
    public static boolean isTheStampInThisMonthly (int monthEndDate,long timeStamp){	
    	int last_end_day,last_end_month,last_end_year;//上一个月结日，月，年
    	int cur_day,cur_month,cur_year;//当前日，月，年
    	int end_day,end_month,end_year;//月结日，月，年
    	
    	Calendar cal = Calendar.getInstance();
    	cur_day = cal.get(Calendar.DATE);
        cur_month = cal.get(Calendar.MONTH)+1;//月份的起始值为０而不是１       
        cur_year = cal.get(Calendar.YEAR);
        
        //确定月结月，月结年
        if(cur_day >= monthEndDate){
        	end_month = cur_month+1;
        	if(end_month > 12){
        		end_month = 1;
        		end_year = cur_year+1;
        	}else{
        		end_year = cur_year;
        	}
        }else{
        	end_month = cur_month;
        	end_year = cur_year;
        }
               
        //确定月结日
        int end_month_last_day = getMonthLastDay(end_year,end_month);
        if(monthEndDate>end_month_last_day){
        	end_day = end_month_last_day;
        }else{
        	end_day = monthEndDate;
        }

        //确定上一个月结月，月结年
        last_end_month = end_month-1;
    	if(last_end_month <= 0){
    		last_end_month = 12;
    		last_end_year= end_year-1;
    	}else{
    		last_end_year = end_year;
    	}
               
        //确定上一个月结日
        int last_end_month_last_day = getMonthLastDay(last_end_year,last_end_month);
        if(monthEndDate>last_end_month_last_day){
        	last_end_day = last_end_month_last_day;
        }else{
        	last_end_day = monthEndDate;
        }
           
        Calendar calendar1 = Calendar.getInstance();
        Calendar calendar2 = Calendar.getInstance();
        calendar1.set(last_end_year, last_end_month-1, last_end_day,0,0,0);//月份的起始值为０而不是１       
        calendar2.set(end_year, end_month-1, end_day,0,0,0);//月份的起始值为０而不是１ 
        long milliseconds1 = calendar1.getTimeInMillis();
        long milliseconds2 = calendar2.getTimeInMillis();
        if(milliseconds1 <= timeStamp && timeStamp <milliseconds2){
        	return true;
        }else{
        	return false;
        }
    }
   
    /**
     * 根据时间戳获取对应的“日期”
     * @param day
     * @return
     */
    public static int timeStampToDay(long timeStamp){         
    	Calendar calendar1 = Calendar.getInstance();
        calendar1.setTimeInMillis(timeStamp);
        return calendar1.get(Calendar.DATE);
    }
    
    /**
     * 根据时间戳获取对应的“日期”
     * @param day
     * @return
     */
    public static int timeStampToMonth(long timeStamp){         
    	Calendar calendar1 = Calendar.getInstance();
        calendar1.setTimeInMillis(timeStamp);
        return calendar1.get(Calendar.MONTH)+1;//月份的起始值为０而不是１ 
    }
    
    /**
     * 获取当前年
     * @return
     */
    public static int getCurYear(){
    	Calendar cal = Calendar.getInstance();   
        return cal.get(Calendar.YEAR);
    }
    
    /**
     * 获取当前月
     * @return
     */
    public static int getCurMonth(){
    	Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.MONTH)+1;//月份的起始值为０而不是１   
    }
    
    /**
     * 获取当前日
     * @return
     */
    public static int getCurDay(){
    	Calendar cal = Calendar.getInstance();
    	return cal.get(Calendar.DATE);
    }
    
 
}
