/*
 * DynamicJasper: A library for creating reports dynamically by specifying
 * columns, groups, styles, etc. at runtime. It also saves a lot of development
 * time in many cases! (http://sourceforge.net/projects/dynamicjasper)
 *
 * Copyright (C) 2008  FDV Solutions (http://www.fdvsolutions.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 *
 * License as published by the Free Software Foundation; either
 *
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *
 */

package ar.com.fdvs.dj.webwork;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRResultSetDataSource;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanArrayDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ar.com.fdvs.dj.core.DJException;
import ar.com.fdvs.dj.core.DynamicJasperHelper;
import ar.com.fdvs.dj.core.layout.ClassicLayoutManager;
import ar.com.fdvs.dj.core.layout.LayoutManager;
import ar.com.fdvs.dj.core.layout.ListLayoutManager;
import ar.com.fdvs.dj.domain.DynamicReport;
import ar.com.fdvs.dj.output.FormatInfoRegistry;
import ar.com.fdvs.dj.output.ReportWriter;
import ar.com.fdvs.dj.output.ReportWriterFactory;

import com.opensymphony.util.TextUtils;
import com.opensymphony.webwork.WebWorkException;
import com.opensymphony.webwork.WebWorkStatics;
import com.opensymphony.webwork.views.jasperreports.JasperReportsResult;
import com.opensymphony.xwork.ActionInvocation;
import com.opensymphony.xwork.util.TextParseUtil;

/**
 * @author Alejandro Gomez
 *         Date: Feb 22, 2007
 *         Time: 4:32:34 PM
 */
public class DJResult extends JasperReportsResult {

	private static final long serialVersionUID = -5135527859073133975L;

	private static final Log LOG = LogFactory.getLog(DJResult.class);

	public static final String LAYOUT_CLASSIC = "classic";
	public static final String LAYOUT_LIST = "list";

    protected String dynamicReport;

    protected String documentFormat;

    /**
     * The layout manager to use. Possible values are: classic, list, or a fully qualified java name
     */
    protected String layoutManager;

	protected String exportParams;

    protected String parameters;

    public void setDynamicReport(final String _dynamicReport) {
        dynamicReport = _dynamicReport;
    }

    /**
     * Executes the result given a final location (jsp page, action, etc) and the action invocation
     * (the state in which the action was executed). Subclasses must implement this class to handle
     * custom logic for result handling.
     *
     * @param _finalLocation the location (jsp page, action, etc) to go to.
     * @param _invocation    the execution state of the action.
     * @throws Exception if an error occurs while executing the result.
     */
    protected void doExecute(final String _finalLocation, final ActionInvocation _invocation) throws Exception {
        checkParams();
        documentFormat = getFormat(_invocation);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating JasperReport for dynamicReport, format = " + documentFormat);
        }


        //construct the dynamic report
        //final OgnlValueStack stack = _invocation.getStack();
        //final JRDataSource ds = (JRDataSource)conditionalParse(dataSource, _invocation, JRDataSource.class);
        final JRDataSource ds = buildJRDataSource(_invocation.getStack().findValue(dataSource));
        //final OgnlValueStackDataSource stackDataSource = new OgnlValueStackDataSource(stack, dataSource);

        // (Map) ActionContext.getContext().getSession().get("IMAGES_MAP");

        final HttpServletRequest request = (HttpServletRequest)_invocation.getInvocationContext().get(WebWorkStatics.HTTP_REQUEST);
        final HttpServletResponse response = (HttpServletResponse)_invocation.getInvocationContext().get(WebWorkStatics.HTTP_RESPONSE);
        if ("contype".equals(request.getHeader("User-Agent"))) {
            // Code to handle "contype" request from IE
            handleConTypeRequest(response);
        } else {
            //final JasperPrint jasperPrint = DynamicJasperHelper.generateJasperPrint(getDynamicReport(_invocation), new ClassicLayoutManager(), stackDataSource);
//            final HashMap parameters = new HashMap();
            Map parameters = _invocation.getStack().getContext(); //FIXME Extend HashMap so that if a key is not found, the look into the stack. Or just use a regular map.
            //TODO set the locale
            parameters.put(JRParameter.REPORT_LOCALE, _invocation.getInvocationContext().getLocale());
            LayoutManager layoutManagerObj = getLayOutManagerObj(_invocation);
			//final JasperPrint jasperPrint = DynamicJasperHelper.generateJasperPrint(getDynamicReport(_invocation), layoutManagerObj, ds, parameters);
			final JasperPrint jasperPrint = DynamicJasperHelper.generateJasperPrint(getDynamicReport(_invocation), layoutManagerObj, ds, parameters);

            // Export the print object to the desired output format
            writeReponse(request, response, jasperPrint, _invocation);
        }
    }

    protected JRDataSource buildJRDataSource(Object dsCandidate) {
		if (dsCandidate == null)
			return null;
		
		if (dsCandidate instanceof JRDataSource)
			return (JRDataSource) dsCandidate;
    	
		if (dsCandidate instanceof Collection)
			return new JRBeanCollectionDataSource((Collection) dsCandidate);
		
		if (dsCandidate instanceof ResultSet)
			return new JRResultSetDataSource((ResultSet) dsCandidate);
		
		if (dsCandidate.getClass().isArray())
			return new JRBeanArrayDataSource((Object[]) dsCandidate);
		
		
		throw new DJException("class " + dsCandidate.getClass().getName() + " is not supported " +
				"from the DynamicJasper WebWorK result type. Provide a JRDataSource implementation instead");
	}

	protected LayoutManager getLayOutManagerObj(ActionInvocation _invocation) {
		String los = conditionalParse(layoutManager, _invocation);
		if (LAYOUT_CLASSIC.equals(los))
			return new ClassicLayoutManager();

		if (LAYOUT_LIST.equals(los))
			return new ListLayoutManager();


		LayoutManager lo = (LayoutManager) conditionalParse(layoutManager, _invocation, LayoutManager.class);

		if (lo != null)
			return lo;

		if (los != null){
			try {
				lo = (LayoutManager) Class.forName(los).newInstance();
			} catch (Exception e) {
				LOG.warn("No valid LayoutManager: " + e.getMessage(),e);
			}
		}

		if (lo == null){
			LOG.warn("No valid LayoutManager, using ClassicLayoutManager");
			lo = new ClassicLayoutManager();
		}

		return lo;
	}

	protected void handleConTypeRequest(final HttpServletResponse _response) throws ServletException {
        try {
            _response.setContentType(FormatInfoRegistry.getInstance().getContentType(documentFormat)); //
            _response.setContentLength(0);
            _response.getOutputStream().close();
        } catch (IOException ex) {
            LOG.error("Error writing report output", ex);
            throw new ServletException(ex.getMessage(), ex);
        }
    }

    protected void checkParams() {
        if (dynamicReport == null) {
            final String message = "No dynamicReport specified...";
            LOG.error(message);
            throw new WebWorkException(message);
        }
        if (dataSource == null) {
            final String message = "No dataSource specified...";
            LOG.error(message);
            throw new WebWorkException(message);
        }
    }

    protected void setResponseHeaders(final HttpServletResponse _response, final ActionInvocation _invocation) {
        if (contentDisposition != null || documentName != null) {
            final StringBuffer buffer = new StringBuffer();
            buffer.append(getContentDisposition(_invocation));
            if (documentName != null) {
                buffer.append("; filename=");
                buffer.append(getDocumentName(_invocation));
                buffer.append(".");
                buffer.append(documentFormat.toLowerCase());
            }
            _response.setHeader("Content-disposition", buffer.toString());
        }
        _response.setContentType(FormatInfoRegistry.getInstance().getContentType(documentFormat));
    }

    protected void writeReponse(final HttpServletRequest _request, final HttpServletResponse _response, final JasperPrint _jasperPrint, final ActionInvocation _invocation) throws JRException, IOException {
        setResponseHeaders(_response, _invocation);
        final HashMap parameters = new HashMap(getExportParams(_invocation));
        parameters.put(JRHtmlExporterParameter.IMAGES_URI, _request.getContextPath() + imageServletUrl);
        final ReportWriter reportWriter = ReportWriterFactory.getInstance().getReportWriter(_jasperPrint, documentFormat, parameters);
        reportWriter.writeTo(_response);
    }

    protected Map getExportParams(final ActionInvocation _invocation) {
    	Map params = (Map)conditionalParse(exportParams, _invocation, Map.class);
    	if (params == null) {
    		params = new HashMap();
    	}
		return params;
    }

    protected DynamicReport getDynamicReport(final ActionInvocation _invocation) {
        //return (DynamicReport)conditionalParse(dynamicReport, _invocation, DynamicReport.class);
        return (DynamicReport)_invocation.getStack().findValue(dynamicReport);
    }

//    private String getDataSource(final ActionInvocation _invocation) {
//        return conditionalParse(dataSource, _invocation);
//    }

    protected String getFormat(final ActionInvocation _invocation) {
        final String parsedFormat = conditionalParse(format == null ? FORMAT_PDF : format, _invocation);
        return TextUtils.stringSet(parsedFormat) ? parsedFormat : FORMAT_PDF;
    }

    protected String getDocumentName(final ActionInvocation _invocation) {
        return conditionalParse(documentName, _invocation);
    }

    protected String getContentDisposition(final ActionInvocation _invocation) {
        final String parsedContentDisposition = conditionalParse(contentDisposition, _invocation);
        return parsedContentDisposition == null ? "inline" : parsedContentDisposition;
    }

//    private String getDelimiter(final ActionInvocation _invocation) {
//        return conditionalParse(delimiter, _invocation);
//    }

//    private String getImageServletUrl(final ActionInvocation _invocation) {
//        return conditionalParse(imageServletUrl, _invocation);
//    }

    protected Object conditionalParse(final String _param, final ActionInvocation _invocation, final Class _type) {
        if (parse && _param != null && _invocation != null) {
            return TextParseUtil.translateVariables('$', _param, _invocation.getStack(), _type, null);
        } else {
            return _param;
        }
    }

	public String getLayoutManager() {
		return layoutManager;
	}

	public void setLayoutManager(String layoutManager) {
		this.layoutManager = layoutManager;
	}

	public String getExportParams() {
		return exportParams;
	}

	public void setExportParams(String exportParams) {
		this.exportParams = exportParams;
	}

	public void setParameters(String parameters) {
		this.parameters = parameters;
	}
}
