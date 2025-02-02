package org.compiere.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.FillMandatoryException;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;

/** 
 *	@author Teo Sarca, SC ARHIPAC SRL
 *	@version $Id
 */
public class MIFixedAsset extends X_I_FixedAsset
{	
	/**
	 * generated serial id 
	 */
	private static final long serialVersionUID = -6394518107160329652L;
	/** Default depreciation method */
	private static final String s_defaultDepreciationType = "SL";
	
    /**
     * UUID based Constructor
     * @param ctx  Context
     * @param I_FixedAsset_UU  UUID key
     * @param trxName Transaction
     */
    public MIFixedAsset(Properties ctx, String I_FixedAsset_UU, String trxName) {
        super(ctx, I_FixedAsset_UU, trxName);
    }

	/**
	 * @param ctx
	 * @param I_FixedAsset_ID
	 * @param trxName
	 */
	public MIFixedAsset (Properties ctx, int I_FixedAsset_ID, String trxName)
	{
		super (ctx, I_FixedAsset_ID, trxName);
	}	//	MIFixedAsset

	/**
	 *  Load Constructor
	 *  @param ctx context
	 *  @param rs result set record
	 */
	public MIFixedAsset (Properties ctx, ResultSet rs, String trxName)
	{
		super (ctx, rs, trxName);
	}	//	MIFixedAsset
	
	/**	
	 *  Create / Load product
	 *	@return product
	 */
	public MProduct getCreateProduct()
	{
		Properties ctx = getCtx();
		String trxName = get_TrxName();
		
		int M_Product_ID = getM_Product_ID();
		if (M_Product_ID <= 0) {
			StringBuilder whereClause = new StringBuilder();
			String key = getProductValue();
			if (key == null || key.trim().length() == 0) {
				key = getName();
				whereClause.append("UPPER(Name)=");
			}
			else {
				whereClause.append("UPPER(Value)=");
			}
			if (key == null || key.trim().length() == 0) {
				throw new FillMandatoryException(COLUMNNAME_ProductValue, COLUMNNAME_Name);
			}
			key = key.toUpperCase();
			whereClause.append(DB.TO_STRING(key));
			whereClause.append(" AND AD_Client_ID=").append(getAD_Client_ID());
			String sql = "SELECT M_Product_ID FROM M_Product WHERE " + whereClause.toString();
			M_Product_ID = DB.getSQLValueEx(trxName, sql);
			if (log.isLoggable(Level.FINE)) log.fine("M_Product_ID=" + M_Product_ID + " -- sql=" + sql);
		}
		
		MProduct prod = null;
		// Create MProduct:
		if (M_Product_ID <= 0)
		{
			prod = new MProduct(ctx, 0, trxName);
			prod.setName(getName());
			String value = getProductValue();
			if (value != null && value.trim().length() > 0) {
				prod.setValue(value);
			}
			
			prod.setM_Product_Category_ID(m_M_Product_Category_ID);
			if (getC_UOM_ID() > 0)
			{
				prod.setC_UOM_ID(getC_UOM_ID());
			}
			else
			{
				prod.setC_UOM_ID(MUOM.getDefault_UOM_ID(ctx));
			}
			// Default Tax Category:
			String sql = "SELECT C_TaxCategory_ID FROM C_TaxCategory WHERE AD_Client_ID IN (0,?) ORDER BY IsDefault DESC, AD_Client_ID DESC, C_TaxCategory_ID";
			int C_TaxCategory_ID = DB.getSQLValueEx(null, sql, Env.getAD_Client_ID(ctx));
			prod.setC_TaxCategory_ID(C_TaxCategory_ID);
			//
			prod.saveEx(trxName);
		}
		else {
			prod = new MProduct(ctx, M_Product_ID, trxName);
		}
		
		setProduct(prod);
		return prod;
	}	//	getCreateProduct
	
	/**
	 * Round value of a column to standard precision.
	 * @param idx column index 
	 */
	private void fixAmount(int idx) {
		BigDecimal amt = (BigDecimal)get_Value(idx);
		if (amt == null)
			return;
		
		int precision = getStdPrecision();
		BigDecimal newAmt = amt.setScale(getStdPrecision(), RoundingMode.HALF_UP);
		set_Value(idx, newAmt);
		if (log.isLoggable(Level.FINE)) log.fine(getInventoryNo() + ": " + get_ColumnName(idx) + "=" + amt + "->" + newAmt + " (precision=" + precision + ")");
	}
	
	/**
	 * Trim and compress duplicate space character in the value of a column 
	 * @param idx column index
	 */
	private void fixKeyValue(int idx) {
		String name = (String)get_Value(idx);
		if (name == null)
			return;
		String newName = name.trim().replaceAll("[ ]+", " ");
		if (log.isLoggable(Level.FINE)) log.fine(getInventoryNo() + ": " + get_ColumnName(idx) + "=[" + name + "]->[" + newName + "]");
		set_Value(idx, newName);
	}
	
	/**
	 * @deprecated
	 */
	@Deprecated
	public void process()
	{
		if (isProcessed()) {
			return ;
		}
		try {
			if (getUseLifeMonths() <= 0)
			{
				throw new FillMandatoryException(COLUMNNAME_UseLifeMonths);
			}
			
			// Round amounts:
			int col_count = get_ColumnCount();
			for (int idx = 0; idx < col_count; idx++)
			{
				int dt = get_ColumnDisplayType(idx);
				if (DisplayType.Amount == dt)
					fixAmount(idx);
				else if (DisplayType.isText(dt))
					fixKeyValue(idx);
			}
			
			// Create/Set Product
			MProduct product = getCreateProduct();
			if (log.isLoggable(Level.FINE)) log.fine("product=" + product);
			if (getM_Product_ID() <= 0) {
				throw new FillMandatoryException(COLUMNNAME_M_Product_ID);
			}
			
			// Check Asset Group
			int A_Asset_Group_ID = getA_Asset_Group_ID();
			if (A_Asset_Group_ID <= 0)
			{
				if (m_A_Asset_Group_ID > 0) {
					A_Asset_Group_ID = m_A_Asset_Group_ID;
				}
				else {
					A_Asset_Group_ID = product.getA_Asset_Group_ID();
				}
			}
			if (A_Asset_Group_ID > 0)
			{
				setA_Asset_Group_ID(A_Asset_Group_ID);
			}
			else
			{
				throw new FillMandatoryException(COLUMNNAME_A_Asset_Group_ID);
			}
			
			// Set DateAcct
			if (getA_Remaining_Period() == 0)
			{
				setDateAcct(getAssetDepreciationDate());
			}
			else
			{
				Timestamp dateAcct = getDateAcct();
				if (dateAcct == null)
				{
					dateAcct = Env.getContextAsDate(getCtx(), Env.DATE);
					setDateAcct(dateAcct);
				}
			}
			if (getDateAcct() == null)
			{
				throw new FillMandatoryException(COLUMNNAME_DateAcct);
			}
			
			// Set Processed
			setProcessed(true);
			setI_ErrorMsg(null);
			
			// Save
			saveEx();
		}
		catch (Exception e)
		{
			setError(e.getLocalizedMessage());
			saveEx();
		}
	}
	
	/**
	 * @return true if fully depreciated
	 */
	public boolean isFullyDepreciated()
	{
		BigDecimal cost = getA_Asset_Cost();
		BigDecimal depr_c = getA_Accumulated_Depr();
		BigDecimal depr_f = getA_Accumulated_Depr_F();
		
		return cost.compareTo(depr_c) == 0 && cost.compareTo(depr_f) == 0;
	}
	
	/**
	 * @return Asset is Depreciating
	 */
	public boolean isDepreciating()
	{
		//change logic to assetGroup
		MAssetGroup assetGroup = MAssetGroup.get(getCtx(), getA_Asset_Group_ID());
		if (assetGroup == null)
			return false;
		return assetGroup.isDepreciated();
		//end modify by @win
	}
	
	/**
	 * @return
	 */
	public int getA_Last_Period()
	{
		int life = getUseLifeMonths();
		int life_f = getUseLifeMonths_F();
		return life > life_f ? life : life_f;
	}
	
	/**				*/
	private int m_M_Product_Category_ID = 0;
	
	/**
	 * SEt default product category id
	 * @param M_Product_Category_ID
	 */
	public void setDefault_Product_Category_ID(int M_Product_Category_ID) {
		m_M_Product_Category_ID = M_Product_Category_ID;
	}
	
	/**				*/
	private int m_A_Asset_Group_ID = 0;
	
	/**
	 * set defauly asset group id 
	 * @param A_Asset_Group_ID
	 */
	public void setDefault_Asset_Group_ID(int A_Asset_Group_ID) {
		m_A_Asset_Group_ID = A_Asset_Group_ID;
	}
	
	/**	Product	*/
	private MProduct m_product = null;
	
	/**
	 * @param product
	 */
	public void setProduct(MProduct product) {
		m_product = product;
		setM_Product_ID(product.get_ID());
		setProductValue(product.getValue());
		if (Util.isEmpty(getName()))
			setName(product.getName());
	}
	
	/**
	 * @return product or null
	 */
	public MProduct getProduct() {
		if (m_product == null && getM_Product_ID() > 0) {
			m_product = new MProduct(getCtx(), getM_Product_ID(), get_TrxName());
		}
		return m_product;
	}
	
	/**	
	 * @return A_Depreciation_ID
	 */
	public int getA_Depreciation_ID() {
		MDepreciation depr = MDepreciation.get(getCtx(), s_defaultDepreciationType);
		return depr != null ? depr.get_ID() : 0;
	}
	
	/**	
	 * @return A_Depreciation_ID
	 */
	public int getA_Depreciation_F_ID() {
		return getA_Depreciation_ID();
	}
	
	/**	
	 * @return standard precision of primary accounting schema 
	 */
	public int getStdPrecision() {
		return MClient.get(getCtx()).getAcctSchema().getStdPrecision();
	}
	
	/**
	 * @return summary text 
	 */
	public String getSummary() {
		return getInventoryNo() + " - " + getName();
	}

	/**	
	 * Sets custom error (I_ErrorMsg)
	 * @param msg
	 */
	public void setError(String msg) {
		String msg_trl = Msg.parseTranslation(getCtx(), msg);
		setI_ErrorMsg(msg_trl);
	}
}
