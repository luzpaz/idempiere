package org.compiere.model;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.compiere.util.CLogMgt;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.idempiere.cache.ImmutablePOSupport;
import org.idempiere.cache.IntPOCopyCache;
import org.idempiere.cache.POCopyCache;
import org.idempiere.fa.exceptions.AssetNotImplementedException;
import org.idempiere.fa.exceptions.AssetNotSupportedException;
import org.idempiere.fa.service.api.DepreciationDTO;
import org.idempiere.fa.service.api.IDepreciationMethod;

/**
 * Depreciation Method (eg. SL, ARH_VAR ...).
 * Note that only SL (straight line) method have been implemented.
 * @author Teo Sarca, SC ARHIPAC SERVICE SRL
 */
public class MDepreciation extends X_A_Depreciation implements ImmutablePOSupport
{
	/**
	 * generated serial id 
	 */
	private static final long serialVersionUID = -4366354698409595086L;

    /**
     * UUID based Constructor
     * @param ctx  Context
     * @param A_Depreciation_UU  UUID key
     * @param trxName Transaction
     */
    public MDepreciation(Properties ctx, String A_Depreciation_UU, String trxName) {
        super(ctx, A_Depreciation_UU, trxName);
    }

	/**
	 * @param ctx
	 * @param A_Depreciation_ID
	 * @param trxName
	 */
	public MDepreciation (Properties ctx, int A_Depreciation_ID, String trxName)
	{
		super (ctx, A_Depreciation_ID, trxName);
	}	//	MDepreciation

	/**
	 *  Load Constructor
	 *  @param ctx context
	 *  @param rs result set record
	 */
	public MDepreciation (Properties ctx, ResultSet rs, String trxName)
	{
		super (ctx, rs, trxName);
	}	//	MDepreciation

	/**
	 * Copy constructor
	 * @param copy
	 */
	public MDepreciation(MDepreciation copy) 
	{
		this(Env.getCtx(), copy);
	}

	/**
	 * Copy constructor
	 * @param ctx
	 * @param copy
	 */
	public MDepreciation(Properties ctx, MDepreciation copy) 
	{
		this(ctx, copy, (String) null);
	}

	/**
	 * Copy constructor
	 * @param ctx
	 * @param copy
	 * @param trxName
	 */
	public MDepreciation(Properties ctx, MDepreciation copy, String trxName) 
	{
		this(ctx, 0, trxName);
		copyPO(copy);
	}
	
	/**		Cache									*/
	private static IntPOCopyCache<Integer,MDepreciation>
	s_cache = new IntPOCopyCache<Integer,MDepreciation>(Table_Name, 5);
	/**		Cache for type							*/
	private static POCopyCache<String,MDepreciation>
	s_cache_forType = new POCopyCache<String,MDepreciation>(Table_Name, Table_Name+"_DepreciationType", 5);
	/**		Static logger							*/
	private static Logger s_log = CLogger.getCLogger(MDepreciation.class);
	/** The accuracy of calculation on depreciation */
	private final static int m_precision = 2;
		
	/* 12 month */
	private static final BigDecimal BD_12 = BigDecimal.valueOf(12);
	
	/**
	 * @param depr
	 */
	private static void addToCache(MDepreciation depr)
	{
		if (depr == null)
		{
			return ;
		}
		
		s_cache.put(depr.get_ID(), depr, e -> new MDepreciation(Env.getCtx(), e));
		String key = "" + depr.getAD_Client_ID() + "_" + depr.getDepreciationType();
		s_cache_forType.put(key, depr, e -> new MDepreciation(Env.getCtx(), e));
	}
 
	/**
	 * Get Depreciation method from cache
	 * @param A_Depreciation_ID depreciation id
	 */
	public static MDepreciation get(int A_Depreciation_ID)
	{
		return get(Env.getCtx(), A_Depreciation_ID);
	}
	
	/**
	 * Get Depreciation method from cache
	 * @param ctx
	 * @param A_Depreciation_ID depreciation id
	 */
	public static MDepreciation get(Properties ctx, int A_Depreciation_ID)
	{
		MDepreciation depr = s_cache.get(A_Depreciation_ID, e -> new MDepreciation(ctx, e));
		if (depr != null)
			return depr;
		
		depr = new MDepreciation(ctx, A_Depreciation_ID, (String)null);
		if (depr.get_ID() == A_Depreciation_ID)
		{
			addToCache(depr);
			return depr;
		}
		return null;
	} // get

	/**
	 * Get Depreciation method
	 * @param depreciationType depreciation type (e.g. SL)
	 */
	public static MDepreciation get(String depreciationType)
	{
		return get(Env.getCtx(), depreciationType);
	}
	
	/**
	 * Get Depreciation method (immutable)
	 * @param ctx
	 * @param depreciationType depreciation type (e.g. SL)
	 */
	public static MDepreciation get(Properties ctx, String depreciationType)
	{
		int AD_Client_ID = Env.getAD_Client_ID(ctx); 
		String key = "" + AD_Client_ID + "_" + depreciationType;
		MDepreciation depr = s_cache_forType.get(key, e -> new MDepreciation(ctx, e));
		if (depr != null)
			return depr;
		
		final String whereClause = COLUMNNAME_DepreciationType+"=?"
									+" AND AD_Client_ID IN (0,?)";
		depr = new Query(ctx, Table_Name, whereClause, null)
						.setOrderBy("AD_Client_ID DESC")
						.setParameters(new Object[]{depreciationType, AD_Client_ID})
						.firstOnly();
		addToCache(depr);
		return (MDepreciation) depr.markImmutable();
	}	//	get
	
	/**
	 * Returns the precision of calculation of depreciation
	 * @return accuracy of calculation of depreciation
	 */
	public static int getPrecision()
	{
		return m_precision;
	}
	
	/**
	 * @return true if needs to be adjusted in last month's amortization 
	 */
	public boolean requireLastPeriodAdjustment()
	{
		return !"ARH_ZERO".equals(getDepreciationType());
	}

	/**
	 * Calculate the value of depreciation over time
	 * @param assetwk -  Depreciation work sheet
	 * @param assetAcct - FA default accounting elements
	 * @param A_Current_Period - current period
	 * @param Accum_Dep accumulated depreciation until present period
	 * @param depreciationMethod
	 * @return amortized value
	 */
	public BigDecimal invoke(MDepreciationWorkfile assetwk, MAssetAcct assetAcct,
								int A_Current_Period, BigDecimal Accum_Dep, IDepreciationMethod depreciationMethod)
	{
		String depreciationType = getDepreciationType();
		BigDecimal retValue = null;
		
		if(CLogMgt.isLevelFinest())
		{
			if (log.isLoggable(Level.FINE)) log.fine("Entering: DepreciationType=" + depreciationType 
						+ ", assetwk=" + assetwk+ ", assetacct=" + assetAcct 
						+ ", A_Current_Period=" + A_Current_Period //+ " (offset=" + offset + ")"
						+ ", Accum_Dep=" + Accum_Dep
			);
		}
		
		if (!canInvoke(assetwk, assetAcct, A_Current_Period, Accum_Dep))
		{
			return BigDecimal.ZERO;
		}
		
		if (depreciationMethod != null) {
			DepreciationDTO depreciationDTO = new DepreciationDTO();
			depreciationDTO.period = A_Current_Period;
			depreciationDTO.salvage = assetwk.getA_Salvage_Value();
			depreciationDTO.totalAmount = assetwk.getA_Asset_Cost();
			depreciationDTO.useFullLife = new BigDecimal(assetwk.getA_Life_Period());// at the moment, int is ok for Thai, but for other country BigDecima is suitable, need to change AD
			depreciationDTO.useFullLifeUnit = Calendar.MONTH;
			depreciationDTO.trxName = assetwk.get_TrxName();
			depreciationDTO.depreciationId = assetwk.get_ID();
			depreciationDTO.inServiceDate = assetwk.getA_Asset().getAssetServiceDate();
			depreciationDTO.accountDate = assetwk.getDateAcct();
			depreciationDTO.accumulatedCost = assetwk.getA_Accumulated_Depr();
			depreciationDTO.startPeriodDepreciation = assetwk.getA_Current_Period();
			depreciationDTO.scale = getPrecision();
			// calculate expense use implement of IDepreciationMethod
			retValue = depreciationMethod.caclulateDepreciation(depreciationDTO);
		}
		else if (depreciationType.equalsIgnoreCase("SL"))
		{
			retValue = apply_SL(assetwk, assetAcct, A_Current_Period, Accum_Dep);
		}
		else if (depreciationType.equalsIgnoreCase("ARH_VAR"))
		{
			retValue = apply_ARH_VAR(assetwk, assetAcct, A_Current_Period, Accum_Dep);
		}
		else if (depreciationType.equalsIgnoreCase("ARH_AD1"))
		{
			retValue = apply_ARH_AD1(assetwk, assetAcct, A_Current_Period, Accum_Dep);
		}
		else if (depreciationType.equalsIgnoreCase("ARH_AD2"))
		{
			retValue = apply_ARH_AD2(assetwk, assetAcct, A_Current_Period, Accum_Dep);
		}
		else if (depreciationType.equalsIgnoreCase("ARH_ZERO"))
		{
			retValue = apply_ARH_ZERO(assetwk, assetAcct, A_Current_Period, Accum_Dep);
		}
		else
		{
			throw new AssetNotSupportedException(COLUMNNAME_DepreciationType, depreciationType);
		}
		//
		if (retValue == null)
		{
			retValue = BigDecimal.ZERO;
		}
		retValue = retValue.setScale(getPrecision(), RoundingMode.HALF_UP);
		//
		if (log.isLoggable(Level.FINE)) log.fine("Leaving: retValue=" + retValue);
		return retValue;
	}	//	invoke

	/**
	 * Check if the method can be invoked to give parameters. <br/>
	 * Not fully implemented - always return true now.
	 * @param	assetwk
	 * @param	assetAcct
	 * @param	A_Current_Period	between 0 to UseLifeMonths - 1
	 * @param	Accum_Dep
	 * @return true if can invoke depreciation
	 */
	public boolean canInvoke(MDepreciationWorkfile assetwk, MAssetAcct assetAcct,
								int A_Current_Period, BigDecimal Accum_Dep)
	{
		if (assetwk == null)
		{
			log.warning("@NotFound@ @A_Depreciation_Workfile_ID@");
			return false;
		}
		//TODO review this method - red1
		int offset = 0 ; //getFixMonthOffset(); this field does not exist in AD nor DB but exists in X_. - red1
		int lifePeriods = assetwk.getUseLifeMonths(assetwk.isFiscal());
		boolean ok = (offset <= A_Current_Period);
		
		if (log.isLoggable(Level.FINEST)) log.finest("A_Current_Period=" + A_Current_Period + ", lifePeriods=" + lifePeriods + " (offset=" + offset + ") ==> OK=" + ok);
		return ok;
	}	//	canInvoke

	/**
	 * @param wk
	 * @param assetAcct
	 * @param A_Current_Period
	 * @param Accum_Dep
	 * @return always return 0
	 */
	private BigDecimal apply_ARH_ZERO (MDepreciationWorkfile wk, MAssetAcct assetAcct,
										int A_Current_Period, BigDecimal Accum_Dep)
	{
		return Env.ZERO;
	}

	/** 
	 *  Linear depreciation regime 
	 *	@param wk Depreciation work sheet
	 *	@param assetAcct Asset account
	 *	@param A_Current_Period current period
	 *	@param Accum_Dep Accumulated depreciation
	 *	@return depreciation for current period
	 */
	private BigDecimal apply_SL (MDepreciationWorkfile wk, MAssetAcct assetAcct,
									int A_Current_Period, BigDecimal Accum_Dep)
	{
		BigDecimal remainingPeriods = new BigDecimal(wk.getRemainingPeriods(A_Current_Period - 1));
		BigDecimal remainingAmt = wk.getRemainingCost(Accum_Dep);
		BigDecimal amtPerPeriod = Env.ZERO;
		if (remainingPeriods.signum() != 0)
		{
			amtPerPeriod = remainingAmt.divide(remainingPeriods, getPrecision(), RoundingMode.HALF_UP);
		}
		
		if (log.isLoggable(Level.FINEST)) log.finest("currentPeriod=" + A_Current_Period + ", remainingAmt=" + remainingAmt + ", remainingPeriods=" + remainingPeriods + " => amtPerPeriod=" + amtPerPeriod);
		
		return amtPerPeriod;
	}

	/**
	 * Accelerated depreciation regime
	 * @param wk
	 * @param acct
	 * @param A_Current_Period
	 * @param Accum_Dep
	 * @return depreciation for current period
	 */
	private BigDecimal apply_ARH_VAR (MDepreciationWorkfile wk, MAssetAcct acct,
										int A_Current_Period, BigDecimal Accum_Dep)
	{
		BigDecimal amt = wk.getActualCost();
		BigDecimal varPercent = acct.getA_Depreciation_Variable_Perc(wk.isFiscal()).setScale(getPrecision(), RoundingMode.HALF_UP);
		BigDecimal assetExp = BigDecimal.ZERO;
		
		// First time in first year
		if (A_Current_Period == 0)
		{
			assetExp = amt.multiply(varPercent);
		}
		// Periods of the first year (without first)
		else if (A_Current_Period < 12)
		{
			// do nothing;
		}
		// Following periods
		else
		{
			BigDecimal remainingAmt = wk.getRemainingCost(Accum_Dep);
			BigDecimal remainingPeriods = new BigDecimal(wk.getRemainingPeriods(A_Current_Period));
			assetExp = remainingAmt.divide(remainingPeriods, getPrecision(), RoundingMode.HALF_UP);
			// logging
			if (log.isLoggable(Level.FINE)) log.fine("remainingAmt=" + remainingAmt + ", remainingPeriods=" + remainingPeriods+ " => assetExp=" + assetExp);
		}
		
		return assetExp;
	}
	

	/** 
	 * Digressive depreciation regime (AD1)
	 * @param wk
	 * @param assetAcct
	 * @param A_Current_Period
	 * @param Accum_Dep	Accumulated depreciation
	 * @return depreciation for current period
	 * TODO	RE TEST IT!
	 */
	private BigDecimal apply_ARH_AD1(MDepreciationWorkfile wk, MAssetAcct assetAcct, int A_Current_Period,BigDecimal Accum_Dep)
	{
		/** FAs' value = acquisition value - the amount recovered */
		BigDecimal assetAmt = wk.getActualCost();
		/** Life in months */
		int A_Life_Period = wk.getA_Life_Period();
		/** Year = integer part of (current period / 12) => first year will be 0 */
		int A_Current_Year = (int)(A_Current_Period / 12);
		/** Life in years = integer part of (the life in months / 12) => first year will be 0 */
		int A_Life_Year = (int)(A_Life_Period / 12);
		
		/** Linear damping coefficient for one year = 1 / total number of years */
		BigDecimal coef_sl = BigDecimal.ONE.divide(new BigDecimal(A_Life_Year), getPrecision() + 2, RoundingMode.DOWN);
		/** Degressive damping coefficient for one year = one-year linear depreciation * coeficient K */
		BigDecimal coef_ad1 = coef_sl.multiply(BigDecimal.valueOf(2.0)); //added by @win
		
		// logging
		if (log.isLoggable(Level.FINE))  {
			log.fine("assetAmt=" + assetAmt + ", A_Life_Period=" + A_Life_Period);
			log.fine("A_Current_Year=" + A_Current_Year + ", A_Life_Year=" + A_Life_Year);
		}
		
		/** Depreciation for the current year, is calculated below */
		BigDecimal amtPerYear = BigDecimal.ZERO;
		/** They went on linear depreciation */
		boolean is_SL = false;
		/** Year linear depreciation = depreciation remaining / number of years remaining (including this year) */
		BigDecimal amt_sl = BigDecimal.ZERO;
		/** Remaining depreciation */
		BigDecimal amt_r = assetAmt;
		
		for (int curr_year = 0; curr_year <= A_Current_Year; curr_year++) {
			if (!is_SL) {
				/** Number of years of use remaining (including current year) */
				int A_RemainingLife_Year = A_Life_Year - curr_year;
				/** Degressive current year depreciation */
				BigDecimal amt_ad1 = amt_r.multiply(coef_ad1);
				/** Year linear depreciation = depreciation remaining / number of years remaining (including this year) */
				amt_sl = amt_r.divide(new BigDecimal(A_RemainingLife_Year), getPrecision(), RoundingMode.HALF_UP);
				// logging
				if (CLogMgt.isLevelFinest()) {
					if (s_log.isLoggable(Level.FINE)) s_log.fine("amt_r=" + amt_r + ", amt_ad1=amt_r*coef_ad1=" + amt_ad1 + ", amt_sl=amt_r/A_RemainingLife_Year=" + amt_sl + ", A_Current_Year=" + A_Current_Year + ", A_RemainingLife_Year=" + A_RemainingLife_Year);
				}
				
				/** If the first year or if the value depreciation value depreciation degressive more linear ... */
				if (curr_year == 0 || amt_ad1.compareTo(amt_sl) >= 0) {
					amtPerYear = amt_ad1;
				}
				else {
					amtPerYear = amt_sl;
					is_SL = true;
					if (s_log.isLoggable(Level.FINE)) s_log.fine("*** PASS IT ON linear amt_sl= " + amt_sl + " ***");
				}
			}
			else {
				amtPerYear = amt_sl;
				s_log.fine("* linear *");
			}
		
			amt_r = amt_r.subtract(amtPerYear);
			if (s_log.isLoggable(Level.FINE)) s_log.fine("year=" + curr_year + ", amtPerYear=" + amtPerYear + ", amt_r=" + amt_r); //logging
		}
		if (s_log.isLoggable(Level.FINE)) s_log.fine("amt_r=" + amt_r + ", amtPerYear=" + amtPerYear); //logging
		
		/** Damping value for the current month */
		BigDecimal assetExp = getPeriodExp(A_Current_Period, amtPerYear);
		
		/** Depreciation refund */
		if (log.isLoggable(Level.FINE)) log.fine("assetExp=" + assetExp);
		return assetExp;
	}
	
	/**
	 *
	 */
	private BigDecimal apply_ARH_AD2(MDepreciationWorkfile wk, MAssetAcct assetAcct, int A_Current_Period,BigDecimal Accum_Dep)
	{
		throw new AssetNotImplementedException("AD2");
	}
	
	/** 
	 *  Calculate the value of depreciation over a month (period). In the last month of the year we add errors from the adjustment calculation.
	 *	@param A_Current_Period		current month's index
	 *	@param amtPerYear			value of depreciation per year
	 *	@return rounded value to the nearest month/decimal getPrecision ()
	 */
	protected BigDecimal getPeriodExp(int A_Current_Period, BigDecimal amtPerYear)
	{
		/** Monthly amount */
		BigDecimal amtPerMonth = amtPerYear.divide(BD_12, getPrecision(), RoundingMode.HALF_UP);
		/** Value adjustment */
		BigDecimal adj = BigDecimal.ZERO;
		/** The amount a month (with adjustments) */
		BigDecimal assetExp = amtPerMonth;
		
		// if last month of the year, calculate the adjustment
		// NOTE: All adjusted value shall be rounded to getPrecision () decimal
		if (A_Current_Period % 12 == 11)
		{
			adj = amtPerYear.subtract(amtPerMonth.multiply(BD_12));
			assetExp = assetExp.add(adj).setScale(getPrecision(), RoundingMode.HALF_UP);
		}
		
		if (log.isLoggable(Level.FINE)) log.fine("amtPerYear=" + amtPerYear + ", amtPerMonth=" + amtPerMonth + ", adj=" + adj + " => assetExp=" + assetExp);
		return assetExp;
	}

	/**
	 * @return always return 0
	 */
	public int getFixMonthOffset() {
		// IDEMPIERE-197 Stabilize Fixed Assets
		// MDepreciationWorkfile is requiring this missing column
		// TODO: Adding this method to compile correctly and future research
		return 0;
	}
	
	@Override
	public MDepreciation markImmutable() {
		if (is_Immutable())
			return this;

		makeImmutable();
		return this;
	}

}
