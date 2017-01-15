package me.fadmaa.orefine.exhibit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.refine.browsing.Engine;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.exporters.StreamExporter;
import com.google.refine.expr.Evaluable;
import com.google.refine.expr.ExpressionUtils;
import com.google.refine.expr.MetaParser;
import com.google.refine.expr.ParsingException;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.util.ParsingUtilities;

public class ExhibitExporter implements StreamExporter{

	@Override
	public String getContentType() {
		return "application/zip";
	}

	private class Expression {
		String col;
		String expr;
		String hiddenName;
		Expression(String c, String e, String h) {
			this.col = c;
			this.expr = e;
			this.hiddenName = h;
		}
	}
	
	private List<Expression> expressions = new ArrayList<Expression>();
	private List<String> numericFacets = new ArrayList<String>();
	
	@Override
	public void export(Project project, Properties options, Engine engine, OutputStream outputStream) throws IOException {
		String html = getHTML(project, options);
		ZipOutputStream out = new ZipOutputStream(outputStream);
		ZipEntry he = new ZipEntry("index.html");
		out.putNextEntry(he);
		out.write(html.getBytes(), 0, html.getBytes().length);
		out.closeEntry();
		
		String css = getCSS();
		ZipEntry se = new ZipEntry("style.css");
		out.putNextEntry(se);
		out.write(css.getBytes(), 0, css.getBytes().length);
		out.closeEntry();
		
		ZipEntry je = new ZipEntry("data.json");
		out.putNextEntry(je);
		writeJSON(project, engine, out);
		out.closeEntry();
		
		out.close();
	}

	private String getCSS() throws IOException{
		InputStream in = this.getClass().getResourceAsStream("/orefine-exhibit.css");
		String css = IOUtils.toString(in);
		in.close();
		return css;
	}
	
	private String getHTML(Project project, Properties options) {
		String prefix = "tmp" + new Random().nextInt(100);
		String allFacetsJson = (String) options.get("allfacets");
		try{
			InputStream in = this.getClass().getResourceAsStream("/template.html");
			String html = IOUtils.toString(in);
			in.close();
			html = html.replaceAll("\\[\\[TITLE\\]\\]", project.getMetadata().getName());
			JSONArray facetsArr = ParsingUtilities.evaluateJsonStringToArray(allFacetsJson);
			StringBuilder builder = new StringBuilder();
			for(int i=0; i < facetsArr.length(); i+=1){
				JSONObject fo = facetsArr.getJSONObject(i);
				String type = fo.has("type") ? fo.getString("type") : "list";
				String fclass = "";
				String fType= null;
				String fcolname = fo.getString("columnName");
                String fexpr = fo.getString("expression").trim();
				//check if it is a complicated GREL expression
				if(! fexpr.equals("value")) {
					expressions.add(new Expression(fcolname, fexpr, prefix + "_" + i));
					fcolname = prefix + "_" + i;
				}
                if ("list".equals(type)) {
                    fclass = "list-facet";
                } else if ("range".equals(type)) {
                	fclass = "range-facet";
                	fType = "Slider";
                	numericFacets.add(fcolname);
                } else if ("timerange".equals(type)) {
                	throw new RuntimeException("Time Range facet not supported. Please remove it");
                } else if ("scatterplot".equals(type)) {
                   throw new RuntimeException("Scatter Plot facet not supported. Please remove it");
                } else if ("text".equals(type)) {
                	fclass = "text-facet";
                	fType = "TextSearch";
                }
                
                String fname = fo.getString("name");
				
				builder.append("<div class=\"").append(fclass).append("\" data-ex-role=\"exhibit-facet\" data-ex-expression=\"");
				builder.append(".").append(fcolname);
				builder.append("\" data-ex-show-missing=\"");
				builder.append("true");//always show missing
				if(fType != null)
					builder.append("\" data-ex-facet-class=\"").append(fType);
				builder.append("\" data-ex-facet-label=\"").append(fname);
				builder.append("\"></div>\n");
			}
			html = html.replace("[[FACETS_HTML]]", builder.toString());
			
			StringBuilder viewsHtml = new StringBuilder();
			viewsHtml.append("<div data-ex-role=\"view\" data-ex-label=\"Table\" data-ex-view-class=\"Tabular\" data-ex-columns=\"");
			for(String c:project.columnModel.getColumnNames()){
				viewsHtml.append(".").append(c).append(",");
			}
			viewsHtml.setLength(viewsHtml.length()-1);
			viewsHtml.append("\"></div>");
			html = html.replace("[[VIEWS_HTML]]", viewsHtml.toString());
			return html;
		} catch(Exception e){
			throw new RuntimeException(e);
		}
	}
	
	private void writeJSON(Project project, Engine engine, OutputStream out) throws IOException {
		RowVisitor visitor = new RowVisitor() {
			JsonGenerator jsonGen = null;
			@Override
			public boolean visit(Project project, int rowIndex, Row row) {
				try {
					jsonGen.writeStartObject();
					jsonGen.writeStringField("label", String.valueOf(rowIndex));
					for(Column col: project.columnModel.columns){
						int index = col.getCellIndex();
						String fieldName = col.getName();
						Object value = row.getCellValue(index);
						if(value != null) {
							if ( (value instanceof Double && !((Double)value).isNaN() && !((Double)value).isInfinite())){
								jsonGen.writeNumberField(fieldName, (Double) value);
							} else if ((value instanceof Float  && !((Float) value).isNaN() && !((Float) value).isInfinite()) ){
								jsonGen.writeNumberField(fieldName, (Float) value);
							} else if (value instanceof Calendar) {
								jsonGen.writeStringField(fieldName, ParsingUtilities.dateToString(((Calendar) value).getTime()));
			                } else if (value instanceof Date) {
			                	jsonGen.writeStringField(fieldName, ParsingUtilities.dateToString((Date) value));
			                } else  {
			                	jsonGen.writeStringField(fieldName, value.toString());
			                }
						}
					}
					for(Expression e: expressions){
						Object v = evaluateExpression(project, e.expr, e.col, row, rowIndex);
						jsonGen.writeStringField(e.hiddenName, v.toString());
					}
					jsonGen.writeEndObject();
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				return false;
			}
			
			@Override
			public void start(Project project) {
				JsonFactory jfactory = new JsonFactory();
				try {
					jsonGen = jfactory.createJsonGenerator(out);
					jsonGen.writeStartObject();
					
					jsonGen.writeFieldName("properties");
					jsonGen.writeStartObject();
					for(String c: numericFacets){
						jsonGen.writeFieldName(c);
						jsonGen.writeStartObject();
						jsonGen.writeStringField("valueType", "number");
						jsonGen.writeEndObject();
					}
					jsonGen.writeEndObject();
					jsonGen.writeFieldName("items");
					jsonGen.writeStartArray();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			
			@Override
			public void end(Project project) {
				try {
					jsonGen.writeEndArray();
					jsonGen.writeEndObject();					
					jsonGen.close();
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
		};
		engine.getAllFilteredRows().accept(project, visitor);
	}
	
	private Object evaluateExpression(Project project, String expression, String columnName, Row row, int rowIndex) throws ParsingException{
		Properties bindings = ExpressionUtils.createBindings(project);
        Evaluable eval = MetaParser.parse(expression);
        int cellIndex = (columnName==null||columnName.equals(""))?-1:project.columnModel.getColumnByName(columnName).getCellIndex();
        Cell cell; 
		if(cellIndex < 0){
         	cell= new Cell(rowIndex,null);
         }else{
         	cell= row.getCell(cellIndex);
         }
        ExpressionUtils.bind(bindings, row, rowIndex, columnName, cell);
        return eval.evaluate(bindings);        
	}

}
