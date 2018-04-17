package fr.eisbm.SBGN_AF_CONVERTER;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.sbgn.SbgnUtil;
import org.sbgn.bindings.Arc;
import org.sbgn.bindings.Arc.Next;
import org.sbgn.bindings.Glyph;
import org.sbgn.bindings.Glyph.Clone;
import org.sbgn.bindings.Port;
import org.sbgn.bindings.SBGNBase.Extension;
import org.sbgn.bindings.SBGNBase.Notes;
import org.sbgn.bindings.Sbgn;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class SBGNML2GraphML {

	private static final String GRAPH_DESCRIPTION_ATTR = "d0";
	private static final String PORTGRAPHICS_ATTR = "d1";
	private static final String PORTGEOMETRY_ATTR = "d2";
	private static final String PORTUSERDATA_ATTR = "d3";
	private static final String NODE_NOTES_ATTR = "d4";
	private static final String NODE_ANNOTATIONS_ATTR = "d5";
	private static final String NODE_BQMODELIS_ATTR = "d6";
	private static final String NODE_BIOLIS_ATTR = "d7";
	private static final String NODE_CLONE_ATTR = "d8";
	private static final String NODE_URL_ATTR = "d9";
	private static final String NODE_DESCRIPT_ATTR = "d10";
	private static final String NODE_ORIENTATION_ATTR = "d11";
	private static final String NODE_GRAPHICS_ATTR = "d12";
	private static final String RESOURCES = "d13";
	private static final String EDGE_URL_ATTR = "d14";
	private static final String EDGE_DESCRIPT_ATTR = "d15";
	private static final String EDGE_GRAPHICS_ATTR = "d16";

	private org.sbgn.bindings.Map map;

	DirectedGraph<Glyph, Arc> graph;
	Map<String, String> mColorMap = new HashMap<String, String>();
	Map<String, GraphMLStyle> mGlyphStyleMap = new HashMap<String, GraphMLStyle>();
	List<GraphMLResource> resourceList = new ArrayList<GraphMLResource>();
	Set<String> visitedGlyphSet = new HashSet<String>();

	SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();

	public static void main(String[] args) {

		convert(FileUtils.IN_SBGN_ED_FILE);
		System.out.println("simulation finished");
	}

	public static void convert(String szInputFileName) {
		SBGNML2GraphML sg = new SBGNML2GraphML();
		
		String szOutFileName = szInputFileName.substring(0, szInputFileName.indexOf(".")).concat(".graphml");
		System.out.println(szOutFileName);
		sg.parseSBGNFile(szInputFileName, szOutFileName);
	}

	public void parseSBGNFile(String szInSBGNFileName, String szOutGraphMLFileName) {

		FileWriter w;

		// Now read from "f" and put the result in "sbgn"
		Sbgn sbgn;
		try {
			sbgn = FileUtils.readFromFile(szInSBGNFileName);

			// map is a container for the glyphs and arcs
			map = (org.sbgn.bindings.Map) sbgn.getMap();

			try {
				w = new FileWriter(szOutGraphMLFileName);
				graph = new DefaultDirectedGraph<Glyph, Arc>(Arc.class);

				// we can get a list of glyphs (nodes) in this map with getGlyph()
				for (Glyph g : map.getGlyph()) {
					graph.addVertex(g);
				}

				// we can get a list of arcs (edges) in this map with getArc()
				for (Arc a : map.getArc()) {
					Glyph source = findGlyph(((Glyph) a.getSource()).getId());
					Glyph target = findGlyph(((Glyph) a.getTarget()).getId());
					
					if ((null != source) && (null != target)) {
						graph.addEdge(source, target);
					}
				}

				parseColorsAndStyles();

				try {
					export(w, graph);
				} catch (TransformerConfigurationException | SAXException e) {
					e.printStackTrace();
				}

			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (JAXBException e2) {
			e2.printStackTrace();
		}
	}

	private void parseColorsAndStyles() {

		if (null != map.getExtension()) {
			for (Element e : map.getExtension().getAny()) {
				if (null != e) {
					for (int i = 0; i < e.getElementsByTagName("colorDefinition").getLength(); i++) {
						Element colorElem = (Element) e.getElementsByTagName("colorDefinition").item(i);
						mColorMap.put(colorElem.getAttribute("id"), colorElem.getAttribute("value"));
					}

					for (int i = 0; i < e.getElementsByTagName("style").getLength(); i++) {
						Element styleElem = (Element) e.getElementsByTagName("style").item(i);
						String szStyleId = styleElem.getAttribute("id");

						Element graphicProperties = (Element) styleElem.getElementsByTagName("g").item(0);

						String szFillColor = mColorMap.get(graphicProperties.getAttribute("fill"));

						int iFontSize = FileUtils.DEFAULT_FONT_SIZE;
						if (!graphicProperties.getAttribute("fontSize").equals("")) {
							iFontSize = Math.round(Float.parseFloat(graphicProperties.getAttribute("fontSize")));
						}

						String szStrokeColor = mColorMap.get(graphicProperties.getAttribute("stroke"));

						String szStrokeWidth = graphicProperties.getAttribute("strokeWidth");

						String[] glyphsList = styleElem.getAttribute("idList").split(" ");

						for (String _glyphId : glyphsList) {
							mGlyphStyleMap.put(_glyphId,
									new GraphMLStyle(szStyleId, szFillColor, iFontSize, szStrokeColor, szStrokeWidth));
						}
					}
				}
			}
		}
	}

	public void export(Writer writer, DirectedGraph<Glyph, Arc> graph)
			throws SAXException, TransformerConfigurationException {
		// Prepare an XML file to receive the GraphML data
		PrintWriter out = new PrintWriter(writer);
		StreamResult streamResult = new StreamResult(out);
		TransformerHandler handler = factory.newTransformerHandler();
		Transformer serializer = handler.getTransformer();
		serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		serializer.setOutputProperty(OutputKeys.STANDALONE, "no");
		serializer.setOutputProperty(OutputKeys.INDENT, "yes");
		handler.setResult(streamResult);
		handler.startDocument();
		AttributesImpl attr = new AttributesImpl();

		// <graphml>

		// FIXME: Is this the proper way to add this attribute?
		attr.addAttribute("", "", "xmlns:java", "CDATA", "http://www.yworks.com/xml/yfiles-common/1.0/java");
		attr.addAttribute("", "", "xmlns:sys", "CDATA",
				"http://www.yworks.com/xml/yfiles-common/markup/primitives/2.0");
		attr.addAttribute("", "", "xmlns:x", "CDATA", "http://www.yworks.com/xml/yfiles-common/markup/2.0");
		attr.addAttribute("", "", "xmlns:xsi", "CDATA", "http://www.w3.org/2001/XMLSchema-instance");
		attr.addAttribute("", "", "xmlns:y", "CDATA", "http://www.yworks.com/xml/graphml");
		attr.addAttribute("", "", "xmlns:yed", "CDATA", "http://www.yworks.com/xml/yed/3");
		attr.addAttribute("", "", "xsi:schemaLocation", "CDATA",
				"http://graphml.graphdrawing.org/xmlns http://www.yworks.com/xml/schema/graphml/1.1/ygraphml.xsd");

		handler.startElement("http://graphml.graphdrawing.org/xmlns", "", "graphml", attr);
		handler.endPrefixMapping("xsi");

		// <key> for graph description attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Description");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "graph");
		attr.addAttribute("", "", "id", "CDATA", GRAPH_DESCRIPTION_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for portgraphics attribute
		attr.clear();
		attr.addAttribute("", "", "for", "CDATA", "port");
		attr.addAttribute("", "", "id", "CDATA", PORTGRAPHICS_ATTR);
		attr.addAttribute("", "", "yfiles.type", "CDATA", "portgraphics");
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for portgeometry attribute
		attr.clear();
		attr.addAttribute("", "", "for", "CDATA", "port");
		attr.addAttribute("", "", "id", "CDATA", PORTGEOMETRY_ATTR);
		attr.addAttribute("", "", "yfiles.type", "CDATA", "portgeometry");
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for portuserdata attribute
		attr.clear();
		attr.addAttribute("", "", "for", "CDATA", "port");
		attr.addAttribute("", "", "id", "CDATA", PORTUSERDATA_ATTR);
		attr.addAttribute("", "", "yfiles.type", "CDATA", "portuserdata");
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for notes attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "notes");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_NOTES_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for annotation attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Annotation");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_ANNOTATIONS_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for bqmodel_is attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Bqmodel_Is");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_BQMODELIS_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for biol_is attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Biol_Is");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_BIOLIS_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for clone attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Clone");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_CLONE_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for url attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "URL");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_URL_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for description attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Description");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_DESCRIPT_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for orientation attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Orientation");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_ORIENTATION_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for nodegraphics attribute
		attr.clear();
		attr.addAttribute("", "", "for", "CDATA", "node");
		attr.addAttribute("", "", "id", "CDATA", NODE_GRAPHICS_ATTR);
		attr.addAttribute("", "", "yfiles.type", "CDATA", "nodegraphics");
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for resources attribute
		attr.clear();
		attr.addAttribute("", "", "for", "CDATA", "graphml");
		attr.addAttribute("", "", "id", "CDATA", RESOURCES);
		attr.addAttribute("", "", "yfiles.type", "CDATA", "resources");
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for edge url attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "URL");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "edge");
		attr.addAttribute("", "", "id", "CDATA", EDGE_URL_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for edge description attribute
		attr.clear();
		attr.addAttribute("", "", "attr.name", "CDATA", "Description");
		attr.addAttribute("", "", "attr.type", "CDATA", "string");
		attr.addAttribute("", "", "for", "CDATA", "edge");
		attr.addAttribute("", "", "id", "CDATA", EDGE_DESCRIPT_ATTR);
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <key> for edge graphics attribute
		attr.clear();
		attr.addAttribute("", "", "for", "CDATA", "edge");
		attr.addAttribute("", "", "id", "CDATA", EDGE_GRAPHICS_ATTR);
		attr.addAttribute("", "", "yfiles.type", "CDATA", "edgegraphics");
		handler.startElement("", "", "key", attr);
		handler.endElement("", "", "key");

		// <graph>
		attr.clear();
		attr.addAttribute("", "", "edgedefault", "CDATA",
				(graph instanceof DirectedGraph<?, ?>) ? "directed" : "undirected");
		attr.addAttribute("", "", "id", "CDATA", "G");
		handler.startElement("", "", "graph", attr);

		attr.clear();
		attr.addAttribute("", "", "key", "string", GRAPH_DESCRIPTION_ATTR);
		handler.startElement("", "", "data", attr);
		handler.endElement("", "", "data");

		// we can get a list of glyphs (nodes) in this map with getGlyph()
		for (Glyph g : map.getGlyph()) {

			if (!visitedGlyphSet.contains(g.getId())) {

				parseGlyph(handler, g);
			}
		}

		// Add all the edges as <edge> elements...
		for (Arc a : map.getArc()) {
			parseArc(handler, a);
		}

		handler.endElement("", "", "graph");

		// <y:Resources/>
		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", RESOURCES);
		handler.startElement("", "", "data", attr);
		attr.clear();
		handler.startElement("", "", "y:Resources", attr);

		for (GraphMLResource resource : resourceList) {
			parseResource(handler, resource);
		}

		handler.endElement("", "", "y:Resources");
		handler.endElement("", "", "data");

		handler.endElement("", "", "graphml");
		handler.endDocument();

		out.flush();
	}

	private void parseResource(TransformerHandler handler, GraphMLResource resource) throws SAXException {
		AttributesImpl attr = new AttributesImpl();
		attr.addAttribute("", "", "id", "CDATA", resource.getId());
		handler.startElement("", "", FileUtils.Y_RESOURCE, attr);

		attr.clear();
		handler.startElement("", "", FileUtils.YED_NODE_REALIZER_ICON, attr);

		// <y:GenericNode>
		attr.clear();
		attr.addAttribute("", "", "configuration", "CDATA", resource.getResourceClass());
		handler.startElement("", "", FileUtils.Y_GENERIC_NODE, attr);

		attr.clear();
		attr.addAttribute("", "", "height", "CDATA", Float.toString(resource.getHeight()));
		attr.addAttribute("", "", "width", "CDATA", Float.toString(resource.getWidth()));
		attr.addAttribute("", "", "x", "CDATA", Float.toString(resource.getXCoord()));
		attr.addAttribute("", "", "y", "CDATA", Float.toString(resource.getYCoord()));
		handler.startElement("", "", FileUtils.Y_GEOMETRY, attr);
		handler.endElement("", "", FileUtils.Y_GEOMETRY);

		attr.clear();
		attr.addAttribute("", "", "color", "CDATA", "#FFFFFF");
		attr.addAttribute("", "", "transparent", "CDATA", "false");
		handler.startElement("", "", FileUtils.Y_FILL, attr);
		handler.endElement("", "", FileUtils.Y_FILL);

		attr.clear();
		attr.addAttribute("", "", "color", "CDATA", "#000000");
		attr.addAttribute("", "", "type", "CDATA", "line");
		attr.addAttribute("", "", "width", "CDATA", "1.0");
		handler.startElement("", "", FileUtils.Y_BORDER_STYLE, attr);
		handler.endElement("", "", FileUtils.Y_BORDER_STYLE);

		attr.clear();
		attr.addAttribute("", "", "alignment", "CDATA", "right");
		attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
		attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
		attr.addAttribute("", "", "fontSize", "CDATA", "12");
		attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
		attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
		attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
		attr.addAttribute("", "", "height", "CDATA", Float.toString(resource.getHeight()));
		attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
		attr.addAttribute("", "", "modelName", "CDATA", "custom");
		attr.addAttribute("", "", "textColor", "CDATA", "#000000");
		attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "visible", "CDATA", "true");
		attr.addAttribute("", "", "width", "CDATA", Float.toString(resource.getWidth()));
		attr.addAttribute("", "", "x", "CDATA", Float.toString(resource.getXCoord()));
		attr.addAttribute("", "", "y", "CDATA", Float.toString(resource.getYCoord()));

		// Content for <y:NodeLabel>
		handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
		if (null != resource.getText()) {
			handler.characters(resource.getText().toCharArray(), 0, resource.getText().length());
		}
		handler.endElement("", "", FileUtils.Y_NODE_LABEL);

		handler.endElement("", "", FileUtils.Y_GENERIC_NODE);
		handler.endElement("", "", FileUtils.YED_NODE_REALIZER_ICON);
		handler.endElement("", "", FileUtils.Y_RESOURCE);
	}

	private void parseArc(TransformerHandler handler, Arc a) throws SAXException {

		Glyph source;
		Glyph target;
		boolean bTag = false;

		if (a.getSource() instanceof Port) {
			source = findNode(((Port) a.getSource()).getId());
		} else {
			source = findNode(((Glyph) a.getSource()).getId());
		}

		if (a.getTarget() instanceof Port) {
			target = findNode(((Port) a.getTarget()).getId());
		} else {
			target = findNode(((Glyph) a.getTarget()).getId());
		}

		if ((null != source) && (null != target)) {

			if ((source.getClazz().equals(FileUtils.SBGN_TAG)) || (target.getClazz().equals(FileUtils.SBGN_TAG))) {
				bTag = true;
			}
			// <edge>
			AttributesImpl attr = new AttributesImpl();
			attr.clear();
			if (null != a.getId()) {
				attr.addAttribute("", "", "id", "CDATA", a.getId());
			}
			attr.addAttribute("", "", "source", "CDATA", source.getId());
			attr.addAttribute("", "", "target", "CDATA", target.getId());

			handler.startElement("", "", "edge", attr);

			// <data key=EDGE_DESCRIPT_ATTR/>
			attr.clear();
			attr.addAttribute("", "", "key", "CDATA", EDGE_DESCRIPT_ATTR);
			handler.startElement("", "", "data", attr);
			handler.endElement("", "", "data");

			// <data key=EDGE_GRAPHICS_ATTR>
			attr.clear();
			attr.addAttribute("", "", "key", "CDATA", EDGE_GRAPHICS_ATTR);
			handler.startElement("", "", "data", attr);

			// <y:PolyLineEdge>
			attr.clear();
			handler.startElement("", "", FileUtils.Y_POLY_LINE_EDGE, attr);

			float sx = 0, sy = 0, tx = 0, ty = 0;
			attr.clear();
			attr.addAttribute("", "", "sx", "CDATA", Float.toString(sx));
			attr.addAttribute("", "", "sy", "CDATA", Float.toString(sy));
			attr.addAttribute("", "", "tx", "CDATA", Float.toString(tx));
			attr.addAttribute("", "", "ty", "CDATA", Float.toString(ty));
	
			handler.startElement("", "", FileUtils.Y_PATH, attr);
			if (a.getNext() != null) {
				for (Next _next : a.getNext()) {
					attr.clear();
					attr.addAttribute("", "", "x", "CDATA", Float.toString(_next.getX()));
					attr.addAttribute("", "", "y", "CDATA", Float.toString(_next.getY()));
					handler.startElement("", "", FileUtils.Y_POINT, attr);
					handler.endElement("", "", FileUtils.Y_POINT);
				}
			}
			handler.endElement("", "", FileUtils.Y_PATH);

			attr.clear();
			attr.addAttribute("", "", "color", "CDATA", "#000000");
			attr.addAttribute("", "", "type", "CDATA", "line");
			attr.addAttribute("", "", "width", "CDATA", "1.0");
			handler.startElement("", "", FileUtils.Y_LINE_STYLE, attr);
			handler.endElement("", "", FileUtils.Y_LINE_STYLE);

			attr.clear();

			if (a.getClazz().equals(FileUtils.SBGN_NECESSARY_STIMULATION)) {
				attr.addAttribute("", "", "source", "CDATA", "none");
				attr.addAttribute("", "", "target", "CDATA", "white_delta_bar");
			} else if (a.getClazz().equals(FileUtils.SBGN_UNKNOWN_INFLUENCE)) {
				attr.addAttribute("", "", "source", "CDATA", "none");
				attr.addAttribute("", "", "target", "CDATA", "white_diamond");
			} else if (a.getClazz().equals(FileUtils.SBGN_NEGATIVE_INFLUENCE)) {
				attr.addAttribute("", "", "source", "CDATA", "none");
				attr.addAttribute("", "", "target", "CDATA", "t_shape");
			} else if (a.getClazz().equals(FileUtils.SBGN_POSITIVE_INFLUENCE)) {
				attr.addAttribute("", "", "source", "CDATA", "none");
				attr.addAttribute("", "", "target", "CDATA", "white_delta");
			} else if (a.getClazz().equals(FileUtils.SBGN_LOGIC_ARC))
			{
				attr.addAttribute("", "", "source", "CDATA", "none");
				attr.addAttribute("", "", "target", "CDATA", "none");
			}
			else{
				System.out.println(a.getClazz());
				attr.addAttribute("", "", "source", "CDATA", "none");
				attr.addAttribute("", "", "target", "CDATA", "none");
			}

			handler.startElement("", "", FileUtils.Y_ARROWS, attr);
			handler.endElement("", "", FileUtils.Y_ARROWS);

			if (a.getGlyph().size() > 0) {
				for (Glyph card : a.getGlyph()) {
					if (card.getClazz().equals(FileUtils.SBGN_CARDINALITY)) {
						String vertexLabel = card.getLabel().getText();
						if (!vertexLabel.equals("0")) {
							attr.clear();
							attr.addAttribute("", "", "alignment", "CDATA", "center");
							attr.addAttribute("", "", "backgroundColor", "CDATA", "#FFFFFF");
							attr.addAttribute("", "", "configuration", "CDATA", "AutoFlippingLabel");
							attr.addAttribute("", "", "distance", "CDATA", "2.0");
							attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
							attr.addAttribute("", "", "fontSize", "CDATA", "12");

							attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
							attr.addAttribute("", "", "height", "CDATA", "18");
							attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
							attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
							attr.addAttribute("", "", "lineColor", "CDATA", "#000000");
							attr.addAttribute("", "", "modelName", "CDATA", "custom");

							attr.addAttribute("", "", "preferredPlacement", "CDATA", "center_on_edge");
							attr.addAttribute("", "", "ratio", "CDATA", "0.5");
							attr.addAttribute("", "", "textColor", "CDATA", "#000000");
							attr.addAttribute("", "", "verticalTextPosition", "CDATA", "bottom");
							attr.addAttribute("", "", "visible", "CDATA", "true");
							attr.addAttribute("", "", "width", "CDATA", "12");

							handler.startElement("", "", FileUtils.Y_EDGE_LABEL, attr);
							handler.characters(vertexLabel.toCharArray(), 0, vertexLabel.length());
							handler.endElement("", "", FileUtils.Y_EDGE_LABEL);
						}
					}
				}
			}

			attr.clear();
			attr.addAttribute("", "", "smoothed", "CDATA", "false");
			handler.startElement("", "", FileUtils.Y_BEND_STYLE, attr);
			handler.endElement("", "", FileUtils.Y_BEND_STYLE);

			handler.endElement("", "", FileUtils.Y_POLY_LINE_EDGE);
			handler.endElement("", "", "data");
			handler.endElement("", "", "edge");
		}
	}

	private void parseGlyph(TransformerHandler handler, Glyph g) throws SAXException {

		AttributesImpl attr = new AttributesImpl();
		attr.addAttribute("", "", "id", "CDATA", g.getId());

		if (g.getClazz().equals(FileUtils.SBGN_COMPLEX) || (g.getClazz().equals(FileUtils.SBGN_COMPARTMENT))) {
			attr.addAttribute("", "", FileUtils.YFILES_FOLDERTYPE, "CDATA", "group");
		}

		handler.startElement("", "", "node", attr);

		// add notes key = NOTES_ATTR
		addNotes(handler, g.getNotes());

		// add annotation key = ANNOTATIONS_ATTR
		addAnnotation(handler, g.getExtension());

		// add clone key = NODE_CLONE_ATTR
		addClone(handler, g.getClone());

		// add orientation key = NODE_ORIENTATION_ATTR
		addOrientation(handler, g.getOrientation());

		// Simple chemical
		if (g.getClazz().equals(FileUtils.SBGN_SIMPLE_CHEMICAL)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_SIMPLE_CHEMICAL, false);
		}
		// simple chemical multimer
		else if (g.getClazz().equals(FileUtils.SBGN_SIMPLE_CHEMICAL_MULTIMER)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_SIMPLE_CHEMICAL, true);
		}
		// Unspecified entity
		else if (g.getClazz().equals(FileUtils.SBGN_UNSPECIFIED_ENTITY)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_UNSPECIFIED_ENTITY, false);
		}
		// perturbing agent
		else if (g.getClazz().equals(FileUtils.SBGN_PERTURBING_AGENT)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_PERTURBING_AGENT, false);
		}
		// phenotype
		else if (g.getClazz().equals(FileUtils.SBGN_PHENOTYPE)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_PHENOTYPE, false);
		}
		// nucleic acid feature
		else if (g.getClazz().equals(FileUtils.SBGN_NUCLEIC_ACID_FEATURE)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_NUCLEIC_ACID_FEATURE, false);
		}
		// nucleic acid feature multimer
		else if (g.getClazz().equals(FileUtils.SBGN_NUCLEIC_ACID_FEATURE_MULTIMER)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_NUCLEIC_ACID_FEATURE, true);
		}

		// submap
		else if (g.getClazz().equals(FileUtils.SBGN_SUBMAP)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_SUBMAP, false);
		}

		// Macromolecule
		else if (g.getClazz().equals(FileUtils.SBGN_MACROMOLECULE)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_MACROMOLECULE, false);
		}

		// Macromolecule multimer
		else if (g.getClazz().equals(FileUtils.SBGN_MACROMOLECULE_MULTIMER)) {
			parseSBGNElement(handler, g, FileUtils.COM_YWORKS_SBGN_MACROMOLECULE, true);
		}

		// Complex
		else if (g.getClazz().equals(FileUtils.SBGN_COMPLEX)) {
			parseComplex(handler, g, false);
		} else if (g.getClazz().equals(FileUtils.SBGN_COMPLEX_MULTIMER)) {
			parseComplex(handler, g, true);
		}

		// Compartment
		else if (g.getClazz().equals(FileUtils.SBGN_COMPARTMENT)) {
			parseCompartment(handler, g);
		}

		// OR OPERATOR
		else if ((g.getClazz().equals(FileUtils.SBGN_OR)) || (g.getClazz().equals(FileUtils.SBGN_AND))
				|| (g.getClazz().equals(FileUtils.SBGN_NOT))) {
			parseSBGNOperator(handler, g);
		}
		// Tag
		else if (g.getClazz().equals(FileUtils.SBGN_TAG)) {
			parseSBGNTag(handler, g);
		}
		// source and sink
		else if (g.getClazz().equals(FileUtils.SBGN_SOURCE_AND_SINK)) {
			parseSBGNSourceAndSink(handler, g);
		}
		

		handler.endElement("", "", "node");
		visitedGlyphSet.add(g.getId());

	}

	private void parseSBGNSourceAndSink(TransformerHandler handler, Glyph g) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		// <data>
		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_GRAPHICS_ATTR);
		handler.startElement("", "", "data", attr);

		// <y:GenericNode>
		attr.clear();
		attr.addAttribute("", "", "configuration", "CDATA", FileUtils.COM_YWORKS_SBGN_SOURCE_AND_SINK);
		handler.startElement("", "", FileUtils.Y_GENERIC_NODE, attr);

		addGeometry(handler, g);

		GraphMLStyle _style = getStyle(g.getId());
		addFillColor(handler, _style);
		addBorderStyle(handler, _style);

		// <y:NodeLabel>
		attr.clear();
		attr.addAttribute("", "", "alignment", "CDATA", "center");
		attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
		attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
		attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(_style.getFontSize()));
		attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
		attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
		attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
		attr.addAttribute("", "", "hasText", "CDATA", "false");
		attr.addAttribute("", "", "height", "CDATA", Float.toString(g.getBbox().getH()));
		attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
		attr.addAttribute("", "", "modelName", "CDATA", "custom");
		attr.addAttribute("", "", "textColor", "CDATA", "#000000");
		attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "visible", "CDATA", "true");
		attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
		attr.addAttribute("", "", "x", "CDATA", Float.toString(g.getBbox().getX()));
		attr.addAttribute("", "", "y", "CDATA", Float.toString(g.getBbox().getY()));

		// Content for <y:NodeLabel>
		handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
		addLabelModel(handler);
		float fValue = 0;
		addModelParameter(handler, fValue);
		handler.endElement("", "", FileUtils.Y_NODE_LABEL);

		handler.endElement("", "", FileUtils.Y_GENERIC_NODE);
		handler.endElement("", "", "data");

	}

	private GraphMLStyle getStyle(String szId) {
		GraphMLStyle _style = new GraphMLStyle();
		if (mGlyphStyleMap.containsKey(szId)) {
			_style.setId(mGlyphStyleMap.get(szId).getId());
			_style.setFillColor(mGlyphStyleMap.get(szId).getFillColor());
			_style.setFontSize(mGlyphStyleMap.get(szId).getFontSize());
			_style.setStrokeColor(mGlyphStyleMap.get(szId).getStrokeColor());
			_style.setStrokeWidth(mGlyphStyleMap.get(szId).getStrokeWidth());
		}
		return _style;
	}

	private void addMultimerStyleProp(TransformerHandler handler) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		handler.startElement("", "", FileUtils.Y_STYLE_PROPERTIES, attr);
		attr.addAttribute("", "", "class", "CDATA", "java.lang.Integer");
		attr.addAttribute("", "", "name", "CDATA", FileUtils.COM_YWORKS_SBGN_STYLE_MCOUNT);
		attr.addAttribute("", "", "value", "CDATA", "2");
		handler.startElement("", "", FileUtils.Y_PROPERTY, attr);
		handler.endElement("", "", FileUtils.Y_PROPERTY);
		handler.endElement("", "", FileUtils.Y_STYLE_PROPERTIES);
	}

	private void parseSBGNOperator(TransformerHandler handler, Glyph g) throws SAXException {
		AttributesImpl attr = new AttributesImpl();
		String vertexLabel = g.getClazz().toUpperCase();

		// <data>
		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_GRAPHICS_ATTR);
		handler.startElement("", "", "data", attr);

		// <y:GenericNode>
		attr.clear();
		attr.addAttribute("", "", "configuration", "CDATA", FileUtils.COM_YWORKS_SBGN_OPERATOR);
		handler.startElement("", "", FileUtils.Y_GENERIC_NODE, attr);

		addGeometry(handler, g);
		GraphMLStyle style = getStyle(g.getId());
		addFillColor(handler, style);
		addBorderStyle(handler, style);

		// <y:NodeLabel>
		attr.clear();
		attr.addAttribute("", "", "alignment", "CDATA", "center");
		attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
		attr.addAttribute("", "", "bottomInset", "CDATA", "0");
		attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
		attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(style.getFontSize()));
		attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
		attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
		attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
		attr.addAttribute("", "", "height", "CDATA", Float.toString(g.getBbox().getH()));
		attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
		attr.addAttribute("", "", "leftInset", "CDATA", "0");
		attr.addAttribute("", "", "modelName", "CDATA", "custom");
		attr.addAttribute("", "", "rightInset", "CDATA", "0");
		attr.addAttribute("", "", "textColor", "CDATA", "#000000");
		attr.addAttribute("", "", "topInset", "CDATA", "0");
		attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "visible", "CDATA", "true");
		attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
		attr.addAttribute("", "", "x", "CDATA", Float.toString(g.getBbox().getX()));
		attr.addAttribute("", "", "y", "CDATA", Float.toString(g.getBbox().getY()));

		// Content for <y:NodeLabel>
		handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
		handler.characters(vertexLabel.toCharArray(), 0, vertexLabel.length());
		addLabelModel(handler);
		float fValue = 0;
		addModelParameter(handler, fValue);
		handler.endElement("", "", FileUtils.Y_NODE_LABEL);

		handler.endElement("", "", FileUtils.Y_GENERIC_NODE);
		handler.endElement("", "", "data");

	}

	private void parseSBGNTag(TransformerHandler handler, Glyph g) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		if (g.getLabel() != null) {
			String vertexLabel = g.getLabel().getText().trim();

			// <data>
			attr.clear();
			attr.addAttribute("", "", "key", "CDATA", NODE_GRAPHICS_ATTR);
			handler.startElement("", "", "data", attr);

			// <y:GenericNode>
			attr.clear();
			attr.addAttribute("", "", "configuration", "CDATA", FileUtils.COM_YWORKS_SBGN_TAG);
			handler.startElement("", "", FileUtils.Y_GENERIC_NODE, attr);

			addGeometry(handler, g);
			GraphMLStyle style = getStyle(g.getId());
			addFillColor(handler, style);
			addBorderStyle(handler, style);

			// <y:NodeLabel>
			attr.clear();
			attr.addAttribute("", "", "textColor", "CDATA", "#000000");
			attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
			attr.addAttribute("", "", "visible", "CDATA", "true");
			attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
			attr.addAttribute("", "", "x", "CDATA", Float.toString(g.getBbox().getX()));
			attr.addAttribute("", "", "y", "CDATA", Float.toString(g.getBbox().getY()));

			// Content for <y:NodeLabel>
			handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
			handler.characters(vertexLabel.toCharArray(), 0, vertexLabel.length());
			addLabelModel(handler);
			float fValue = 0;
			addModelParameter(handler, fValue);
			handler.endElement("", "", FileUtils.Y_NODE_LABEL);

			if (g.getOrientation().equals("right")) {
				attr.clear();
				handler.startElement("", "", FileUtils.Y_STYLE_PROPERTIES, attr);
				attr.addAttribute("", "", "class", "CDATA", "java.lang.Boolean");
				attr.addAttribute("", "", "name", "CDATA", "com.yworks.sbgn.style.inverse");
				attr.addAttribute("", "", "value", "CDATA", "true");
				handler.startElement("", "", FileUtils.Y_PROPERTY, attr);
				handler.endElement("", "", FileUtils.Y_PROPERTY);
				handler.endElement("", "", FileUtils.Y_STYLE_PROPERTIES);
			}

			handler.endElement("", "", FileUtils.Y_GENERIC_NODE);
			handler.endElement("", "", "data");
		}

	}

	private void parseComplex(TransformerHandler handler, Glyph g, boolean bIsMultimer) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		// <data>
		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_GRAPHICS_ATTR);
		handler.startElement("", "", "data", attr);

		attr.clear();
		handler.startElement("", "", FileUtils.Y_PROXY_AUTO_BOUNDS_NODE, attr);

		attr.clear();
		attr.addAttribute("", "", "active", "CDATA", "0");
		handler.startElement("", "", FileUtils.Y_REALIZERS, attr);
		boolean closed = false;
		GraphMLStyle style = getStyle(g.getId());

		for (int i = 0; i < 2; i++) {

			attr.clear();
			attr.addAttribute("", "", "configuration", "CDATA", FileUtils.COM_YWORKS_SBGN_COMPLEX);
			handler.startElement("", "", FileUtils.Y_GENERIC_GROUP_NODE, attr);

			addGeometry(handler, g);
			addFillColor(handler, style);
			addBorderStyle(handler, style);

			// <y:NodeLabel>
			attr.clear();
			attr.addAttribute("", "", "alignment", "CDATA", "center");
			attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
			attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
			attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(style.getFontSize()));
			attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
			attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
			attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
			attr.addAttribute("", "", "height", "CDATA", Float.toString(g.getBbox().getH()));
			attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
			attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
			attr.addAttribute("", "", "modelName", "CDATA", "internal");
			attr.addAttribute("", "", "modelPosition", "CDATA", "t");
			attr.addAttribute("", "", "textColor", "CDATA", "#000000");
			attr.addAttribute("", "", "verticalTextPosition", "CDATA", "top");
			attr.addAttribute("", "", "visible", "CDATA", "true");
			attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
			attr.addAttribute("", "", "x", "CDATA", Float.toString(g.getBbox().getX()));
			attr.addAttribute("", "", "y", "CDATA", Float.toString(g.getBbox().getY()));

			handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
			if (null != g.getLabel()) {
				String vertexLabel = g.getLabel().getText().trim();
				handler.characters(vertexLabel.toCharArray(), 0, vertexLabel.length());
			}
			handler.endElement("", "", FileUtils.Y_NODE_LABEL);

			// the unit of information for complexes is represented as a resource but its
			// name label is given immediately after the complex label in yEd
			if (false == closed) {
				if (g.getGlyph().size() > 0) {
					for (Glyph childGlyph : g.getGlyph()) {
						if ((childGlyph.getClazz().equals(FileUtils.SBGN_UNIT_OF_INFORMATION))
								|| (childGlyph.getClazz().equals(FileUtils.SBGN_STATE_VARIABLE))) {
							attr.clear();

							attr.addAttribute("", "", "alignment", "CDATA", "center");
							attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
							attr.addAttribute("", "", "borderDistance", "CDATA", "0.0");
							attr.addAttribute("", "", "bottomInset", "CDATA", "0");
							attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
							attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(style.getFontSize()));
							attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
							attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
							attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
							attr.addAttribute("", "", "hasText", "CDATA", "false");

							attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
							attr.addAttribute("", "", "iconData", "CDATA", childGlyph.getId());
							attr.addAttribute("", "", "iconTextGap", "CDATA", "0");
							attr.addAttribute("", "", "leftInset", "CDATA", "0");
							attr.addAttribute("", "", "modelName", "CDATA", "custom");
							attr.addAttribute("", "", "rightInset", "CDATA", "0");
							attr.addAttribute("", "", "textColor", "CDATA", "#000000");
							attr.addAttribute("", "", "topInset", "CDATA", "0");
							attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
							attr.addAttribute("", "", "visible", "CDATA", "true");

							attr.addAttribute("", "", "height", "CDATA", Float.toString(childGlyph.getBbox().getH()));
							attr.addAttribute("", "", "width", "CDATA", Float.toString(childGlyph.getBbox().getW()));
							attr.addAttribute("", "", "x", "CDATA", Float.toString(childGlyph.getBbox().getX()));
							attr.addAttribute("", "", "y", "CDATA", Float.toString(childGlyph.getBbox().getY()));

							// Content for <y:NodeLabel>
							handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
							addLabelModel(handler);
							float fValueStateVariable = (float) 0.5;
							addModelParameter(handler, fValueStateVariable);
							handler.endElement("", "", FileUtils.Y_NODE_LABEL);

							GraphMLResource _resource = new GraphMLResource();
							_resource.setId(childGlyph.getId());
							String szText = "";

							if (childGlyph.getClazz().equals(FileUtils.SBGN_UNIT_OF_INFORMATION)) {
								_resource.setClass(FileUtils.COM_YWORKS_SBGN_UNIT_OF_INFORMATION);
								if (childGlyph.getLabel() != null) {
									szText = childGlyph.getLabel().getText();
								}
							} else {
								_resource.setClass(FileUtils.COM_YWORKS_SBGN_STATE_VARIABLE);
								if (childGlyph.getState() != null) {

									if (childGlyph.getState().getValue() != null) {
										szText = szText.concat(childGlyph.getState().getValue());
									}

									if (childGlyph.getState().getVariable() != null) {
										szText = szText.concat("@" + childGlyph.getState().getVariable());
									}
								}
							}
							_resource.setText(szText);
							_resource.setHeight(childGlyph.getBbox().getH());
							_resource.setWidth(childGlyph.getBbox().getW());
							_resource.setXCoord(childGlyph.getBbox().getX());
							_resource.setYCoord(childGlyph.getBbox().getY());

							resourceList.add(_resource);
						}
					}
				}
			}

			attr.clear();
			handler.startElement("", "", FileUtils.Y_STYLE_PROPERTIES, attr);

			for (int j = 0; j < 2; j++) {
				attr.clear();
				attr.addAttribute("", "", "class", "CDATA", "java.lang.Integer");
				attr.addAttribute("", "", "name", "CDATA", "com.yworks.sbgn.style.radiusY");
				attr.addAttribute("", "", "value", "CDATA", Integer.toString(12));
				handler.startElement("", "", FileUtils.Y_PROPERTY, attr);
				handler.endElement("", "", FileUtils.Y_PROPERTY);
			}
			handler.endElement("", "", FileUtils.Y_STYLE_PROPERTIES);

			attr.clear();
			attr.addAttribute("", "", "autoResize", "CDATA", "true");
			attr.addAttribute("", "", "closed", "CDATA", Boolean.toString(closed));
			attr.addAttribute("", "", "closedHeight", "CDATA", Integer.toString(80));
			attr.addAttribute("", "", "closedWidth", "CDATA", Integer.toString(80));
			handler.startElement("", "", FileUtils.Y_STATE, attr);
			handler.endElement("", "", FileUtils.Y_STATE);

			attr.clear();
			attr.addAttribute("", "", "bottom", "CDATA", "15");
			attr.addAttribute("", "", "bottomF", "CDATA", "15.0");
			attr.addAttribute("", "", "left", "CDATA", "10");
			attr.addAttribute("", "", "leftF", "CDATA", "10.0");
			attr.addAttribute("", "", "right", "CDATA", "10");
			attr.addAttribute("", "", "rightF", "CDATA", "10.0");
			attr.addAttribute("", "", "top", "CDATA", "10");
			attr.addAttribute("", "", "topF", "CDATA", "10.0");
			handler.startElement("", "", FileUtils.Y_INSETS, attr);
			handler.endElement("", "", FileUtils.Y_INSETS);

			attr.clear();
			attr.addAttribute("", "", "bottom", "CDATA", "0");
			attr.addAttribute("", "", "bottomF", "CDATA", "0.0");
			attr.addAttribute("", "", "left", "CDATA", "0");
			attr.addAttribute("", "", "leftF", "CDATA", "0.0");
			attr.addAttribute("", "", "right", "CDATA", "0");
			attr.addAttribute("", "", "rightF", "CDATA", "0.0");
			if (closed) {
				attr.addAttribute("", "", "top", "CDATA", "0");
				attr.addAttribute("", "", "topF", "CDATA", "0.0");
			} else {
				attr.addAttribute("", "", "top", "CDATA", "3");
				attr.addAttribute("", "", "topF", "CDATA", "3.0");
			}
			handler.startElement("", "", FileUtils.Y_BORDER_INSETS, attr);
			handler.endElement("", "", FileUtils.Y_BORDER_INSETS);

			handler.endElement("", "", FileUtils.Y_GENERIC_GROUP_NODE);
			closed = !closed;
		}
		handler.endElement("", "", FileUtils.Y_REALIZERS);
		handler.endElement("", "", FileUtils.Y_PROXY_AUTO_BOUNDS_NODE);
		handler.endElement("", "", "data");

		attr.clear();
		attr.addAttribute("", "", "edgedefault", "CDATA",
				(graph instanceof DirectedGraph<?, ?>) ? "directed" : "undirected");
		attr.addAttribute("", "", "id", "CDATA", g.getId());
		handler.startElement("", "", "graph", attr);

		if (g.getGlyph().size() > 0) {

			for (Glyph childGlyph : g.getGlyph()) {
				if (!childGlyph.getClazz().equals(FileUtils.SBGN_UNIT_OF_INFORMATION)
						&& !childGlyph.getClazz().equals(FileUtils.SBGN_STATE_VARIABLE)) {
					parseGlyph(handler, childGlyph);
				}
			}
		}

		handler.endElement("", "", "graph");
	}

	private void parseCompartment(TransformerHandler handler, Glyph g) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		// <data>
		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_GRAPHICS_ATTR);
		handler.startElement("", "", "data", attr);

		attr.clear();
		handler.startElement("", "", FileUtils.Y_PROXY_AUTO_BOUNDS_NODE, attr);

		attr.clear();
		attr.addAttribute("", "", "active", "CDATA", "0");
		handler.startElement("", "", FileUtils.Y_REALIZERS, attr);
		boolean closed = false;
		for (int i = 0; i < 2; i++) {

			attr.clear();
			handler.startElement("", "", FileUtils.Y_GROUP_NODE, attr);

			addGeometry(handler, g);
			GraphMLStyle _style = getStyle(g.getId());
			addFillColor(handler, _style);
			addBorderStyle(handler, _style);

			// <y:NodeLabel>
			attr.clear();
			attr.addAttribute("", "", "alignment", "CDATA", "center");
			attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
			attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
			attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(_style.getFontSize()));
			attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
			attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
			attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
			attr.addAttribute("", "", "height", "CDATA", Float.toString(g.getBbox().getH()));
			attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
			attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
			attr.addAttribute("", "", "modelName", "CDATA", "internal");
			attr.addAttribute("", "", "modelPosition", "CDATA", "t");
			attr.addAttribute("", "", "textColor", "CDATA", "#000000");
			attr.addAttribute("", "", "verticalTextPosition", "CDATA", "bottom");
			attr.addAttribute("", "", "visible", "CDATA", "true");
			attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
			attr.addAttribute("", "", "x", "CDATA", Float.toString(g.getBbox().getX()));
			attr.addAttribute("", "", "y", "CDATA", Float.toString(g.getBbox().getY()));

			handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
			if (null != g.getLabel()) {
				String vertexLabel = g.getLabel().getText().trim();
				handler.characters(vertexLabel.toCharArray(), 0, vertexLabel.length());
			}
			handler.endElement("", "", FileUtils.Y_NODE_LABEL);

			attr.clear();
			attr.addAttribute("", "", "type", "CDATA", "roundrectangle");
			handler.startElement("", "", FileUtils.Y_SHAPE, attr);
			handler.endElement("", "", FileUtils.Y_SHAPE);

			attr.clear();
			attr.addAttribute("", "", "closed", "CDATA", Boolean.toString(closed));
			attr.addAttribute("", "", "closedHeight", "CDATA", Integer.toString(80));
			attr.addAttribute("", "", "closedWidth", "CDATA", Integer.toString(80));
			attr.addAttribute("", "", "innerGraphDisplayEnabled", "CDATA", "false");
			handler.startElement("", "", FileUtils.Y_STATE, attr);
			handler.endElement("", "", FileUtils.Y_STATE);

			attr.clear();
			attr.addAttribute("", "", "bottom", "CDATA", "15");
			attr.addAttribute("", "", "bottomF", "CDATA", "15.0");
			attr.addAttribute("", "", "left", "CDATA", "15");
			attr.addAttribute("", "", "leftF", "CDATA", "15.0");
			attr.addAttribute("", "", "right", "CDATA", "15");
			attr.addAttribute("", "", "rightF", "CDATA", "15.0");
			attr.addAttribute("", "", "top", "CDATA", "15");
			attr.addAttribute("", "", "topF", "CDATA", "15.0");
			handler.startElement("", "", FileUtils.Y_INSETS, attr);
			handler.endElement("", "", FileUtils.Y_INSETS);

			attr.clear();
			attr.addAttribute("", "", "bottom", "CDATA", "0");
			attr.addAttribute("", "", "bottomF", "CDATA", "0.0");
			attr.addAttribute("", "", "left", "CDATA", "0");
			attr.addAttribute("", "", "leftF", "CDATA", "0.0");
			attr.addAttribute("", "", "right", "CDATA", "0");
			attr.addAttribute("", "", "rightF", "CDATA", "0.0");
			attr.addAttribute("", "", "top", "CDATA", "0");
			attr.addAttribute("", "", "topF", "CDATA", "0.0");
			handler.startElement("", "", FileUtils.Y_BORDER_INSETS, attr);
			handler.endElement("", "", FileUtils.Y_BORDER_INSETS);

			handler.endElement("", "", FileUtils.Y_GROUP_NODE);
			closed = !closed;
		}
		handler.endElement("", "", FileUtils.Y_REALIZERS);
		handler.endElement("", "", FileUtils.Y_PROXY_AUTO_BOUNDS_NODE);
		handler.endElement("", "", "data");

		attr.clear();
		attr.addAttribute("", "", "edgedefault", "CDATA",
				(graph instanceof DirectedGraph<?, ?>) ? "directed" : "undirected");
		attr.addAttribute("", "", "id", "CDATA", g.getId());
		handler.startElement("", "", "graph", attr);

		for (Glyph refGlyph : map.getGlyph()) {
			if (refGlyph.getCompartmentRef() != null) {
				if ((((Glyph) (refGlyph.getCompartmentRef())).getId().equals(g.getId()))
						&& (!visitedGlyphSet.contains(refGlyph.getId()))) {
					parseGlyph(handler, refGlyph);
				}
			}

		}
		handler.endElement("", "", "graph");
	}

	private void parseSBGNElement(TransformerHandler handler, Glyph g, String szConfiguration, boolean bIsMultimer)
			throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		if (g.getLabel() != null) {
			String vertexLabel = g.getLabel().getText();

			// <data>
			attr.clear();
			attr.addAttribute("", "", "key", "CDATA", NODE_GRAPHICS_ATTR);
			handler.startElement("", "", "data", attr);

			// <y:GenericNode>
			attr.clear();
			attr.addAttribute("", "", "configuration", "CDATA", szConfiguration);
			handler.startElement("", "", FileUtils.Y_GENERIC_NODE, attr);

			addGeometry(handler, g);
			GraphMLStyle style = getStyle(g.getId());
			addFillColor(handler, style);
			addBorderStyle(handler, style);

			// <y:NodeLabel>
			attr.clear();
			attr.addAttribute("", "", "alignment", "CDATA", "center");
			attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
			attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
			attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(style.getFontSize()));
			attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
			attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
			attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
			attr.addAttribute("", "", "height", "CDATA", Float.toString(g.getBbox().getH()));
			attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
			attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
			attr.addAttribute("", "", "modelName", "CDATA", "custom");
			attr.addAttribute("", "", "textColor", "CDATA", "#000000");
			attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
			attr.addAttribute("", "", "visible", "CDATA", "true");
			attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
			attr.addAttribute("", "", "x", "CDATA", Float.toString(g.getBbox().getX()));
			attr.addAttribute("", "", "y", "CDATA", Float.toString(g.getBbox().getY()));

			// Content for <y:NodeLabel>
			handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
			handler.characters(vertexLabel.toCharArray(), 0, vertexLabel.length());
			addLabelModel(handler);
			float fValue = 0;
			addModelParameter(handler, fValue);
			handler.endElement("", "", FileUtils.Y_NODE_LABEL);

			if (g.getGlyph().size() > 0) {

				for (Glyph childGlyph : g.getGlyph()) {

					if (childGlyph.getClazz().equals(FileUtils.SBGN_STATE_VARIABLE)) {
						attr.clear();

						attr.addAttribute("", "", "alignment", "CDATA", "center");
						attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
						attr.addAttribute("", "", "borderDistance", "CDATA", "0.0");
						attr.addAttribute("", "", "bottomInset", "CDATA", "0");
						attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
						attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(style.getFontSize()));
						attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
						attr.addAttribute("", "", "hasBackgroundColor", "CDATA", "false");
						attr.addAttribute("", "", "hasLineColor", "CDATA", "false");
						attr.addAttribute("", "", "hasText", "CDATA", "false");

						attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
						attr.addAttribute("", "", "iconData", "CDATA", childGlyph.getId());
						attr.addAttribute("", "", "iconTextGap", "CDATA", "0");
						attr.addAttribute("", "", "leftInset", "CDATA", "0");
						attr.addAttribute("", "", "modelName", "CDATA", "custom");
						attr.addAttribute("", "", "rightInset", "CDATA", "0");
						attr.addAttribute("", "", "textColor", "CDATA", "#000000");
						attr.addAttribute("", "", "topInset", "CDATA", "0");
						attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
						attr.addAttribute("", "", "visible", "CDATA", "true");

						attr.addAttribute("", "", "height", "CDATA", Float.toString(childGlyph.getBbox().getH()));
						attr.addAttribute("", "", "width", "CDATA", Float.toString(childGlyph.getBbox().getW()));
						attr.addAttribute("", "", "x", "CDATA", Float.toString(childGlyph.getBbox().getX()));
						attr.addAttribute("", "", "y", "CDATA", Float.toString(childGlyph.getBbox().getY()));

						// Content for <y:NodeLabel>
						handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
						addLabelModel(handler);
						float fValueStateVariable = (float) 0.5;
						addModelParameter(handler, fValueStateVariable);
						handler.endElement("", "", FileUtils.Y_NODE_LABEL);

						GraphMLResource _resource = new GraphMLResource();
						_resource.setId(childGlyph.getId());
						_resource.setClass(FileUtils.COM_YWORKS_SBGN_STATE_VARIABLE);
						_resource.setHeight(childGlyph.getBbox().getH());
						_resource.setWidth(childGlyph.getBbox().getW());
						_resource.setXCoord(childGlyph.getBbox().getX());
						_resource.setYCoord(childGlyph.getBbox().getY());
						if (childGlyph.getState() != null) {
							String szText = "";
							if (childGlyph.getState().getValue() != null) {
								szText = szText.concat(childGlyph.getState().getValue());
							}

							if (childGlyph.getState().getVariable() != null) {
								szText = szText.concat("@" + childGlyph.getState().getVariable());
							}
							_resource.setText(szText);
						}

						resourceList.add(_resource);
					} else if (childGlyph.getClazz().equals(FileUtils.SBGN_UNIT_OF_INFORMATION)) {
						addUnitOfInformation(handler, style, childGlyph);
					}
				}
			}

			if (g.getClazz().equals(FileUtils.SBGN_MACROMOLECULE)) {
				attr.clear();
				handler.startElement("", "", FileUtils.Y_STYLE_PROPERTIES, attr);
				attr.clear();
				attr.addAttribute("", "", "class", "CDATA", "java.lang.Integer");
				attr.addAttribute("", "", "name", "CDATA", "com.yworks.sbgn.style.radius");
				attr.addAttribute("", "", "value", "CDATA", "10");
				handler.startElement("", "", FileUtils.Y_PROPERTY, attr);
				handler.endElement("", "", FileUtils.Y_PROPERTY);
				handler.endElement("", "", FileUtils.Y_STYLE_PROPERTIES);
			}

			if (bIsMultimer) {
				addMultimerStyleProp(handler);
			}

			handler.endElement("", "", FileUtils.Y_GENERIC_NODE);
			handler.endElement("", "", "data");
		}
	}

	private void addUnitOfInformation(TransformerHandler handler, GraphMLStyle _style, Glyph _unitGlyph)
			throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		attr.addAttribute("", "", "alignment", "CDATA", "center");
		attr.addAttribute("", "", "autoSizePolicy", "CDATA", "content");
		attr.addAttribute("", "", "backgroundColor", "CDATA", "#FFFFFF");
		attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
		attr.addAttribute("", "", "fontSize", "CDATA", String.valueOf(_style.getFontSize()));
		attr.addAttribute("", "", "fontStyle", "CDATA", "plain");
		attr.addAttribute("", "", "height", "CDATA", Float.toString(_unitGlyph.getBbox().getH()));
		attr.addAttribute("", "", "width", "CDATA", Float.toString(_unitGlyph.getBbox().getW()));

		attr.addAttribute("", "", "horizontalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "iconTextGap", "CDATA", "4");
		attr.addAttribute("", "", "lineColor", "CDATA", "#000000");
		attr.addAttribute("", "", "modelName", "CDATA", "custom");
		attr.addAttribute("", "", "textColor", "CDATA", "#000000");
		attr.addAttribute("", "", "verticalTextPosition", "CDATA", "center");
		attr.addAttribute("", "", "visible", "CDATA", "true");

		attr.addAttribute("", "", "borderDistance", "CDATA", "0.0");
		attr.addAttribute("", "", "bottomInset", "CDATA", "0");
		attr.addAttribute("", "", "fontFamily", "CDATA", "Dialog");
		attr.addAttribute("", "", "x", "CDATA", Float.toString(_unitGlyph.getBbox().getX()));
		attr.addAttribute("", "", "y", "CDATA", Float.toString(_unitGlyph.getBbox().getY()));

		// Content for <y:NodeLabel>
		handler.startElement("", "", FileUtils.Y_NODE_LABEL, attr);
		String unitLabel = _unitGlyph.getLabel().getText();
		handler.characters(unitLabel.toCharArray(), 0, unitLabel.length());
		addLabelModel(handler);
		float fValueUnitOfInfo = (float) -0.5;
		addModelParameter(handler, fValueUnitOfInfo);
		handler.endElement("", "", FileUtils.Y_NODE_LABEL);
	}

	private void addModelParameter(TransformerHandler handler, float fNodeRatioY) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		handler.startElement("", "", FileUtils.Y_MODEL_PARAMETER, attr);
		attr.addAttribute("", "", "labelRatioX", "CDATA", "0.0");
		attr.addAttribute("", "", "labelRatioY", "CDATA", "0.0");
		attr.addAttribute("", "", "nodeRatioX", "CDATA", "0.0");
		attr.addAttribute("", "", "nodeRatioY", "CDATA", Float.toString(fNodeRatioY));
		attr.addAttribute("", "", "offsetX", "CDATA", "0.0");
		attr.addAttribute("", "", "offsetY", "CDATA", "0.0");
		attr.addAttribute("", "", "upX", "CDATA", "0.0");
		attr.addAttribute("", "", "upY", "CDATA", "-1.0");
		handler.startElement("", "", FileUtils.Y_SMART_NODE_LABEL_MODEL_PARAMETER, attr);
		handler.endElement("", "", FileUtils.Y_SMART_NODE_LABEL_MODEL_PARAMETER);
		handler.endElement("", "", FileUtils.Y_MODEL_PARAMETER);
	}

	private void addLabelModel(TransformerHandler handler) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		handler.startElement("", "", FileUtils.Y_LABEL_MODEL, attr);
		attr.addAttribute("", "", "distance", "CDATA", "4.0");
		handler.startElement("", "", FileUtils.Y_SMART_NODE_LABEL_MODEL, attr);
		handler.endElement("", "", FileUtils.Y_SMART_NODE_LABEL_MODEL);
		handler.endElement("", "", FileUtils.Y_LABEL_MODEL);
	}

	private void addGeometry(TransformerHandler handler, Glyph g) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		attr.addAttribute("", "", "height", "CDATA", Float.toString(g.getBbox().getH()));
		attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
		attr.addAttribute("", "", "x", "CDATA", Float.toString(g.getBbox().getX()));
		attr.addAttribute("", "", "y", "CDATA", Float.toString(g.getBbox().getY()));
		handler.startElement("", "", FileUtils.Y_GEOMETRY, attr);
		handler.endElement("", "", FileUtils.Y_GEOMETRY);
	}

	private void addGeometryForStateVariable(TransformerHandler handler, Glyph g, float x, float y)
			throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		attr.addAttribute("", "", "height", "CDATA", Float.toString(g.getBbox().getH()));
		attr.addAttribute("", "", "width", "CDATA", Float.toString(g.getBbox().getW()));
		attr.addAttribute("", "", "x", "CDATA", Float.toString(x));
		attr.addAttribute("", "", "y", "CDATA", Float.toString(y));
		handler.startElement("", "", FileUtils.Y_GEOMETRY, attr);
		handler.endElement("", "", FileUtils.Y_GEOMETRY);
	}

	private void addBorderStyle(TransformerHandler handler, GraphMLStyle style) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		attr.addAttribute("", "", "color", "CDATA", style.getStrokeColor());
		attr.addAttribute("", "", "type", "CDATA", "line");
		attr.addAttribute("", "", "width", "CDATA", style.getStrokeWidth());
		handler.startElement("", "", FileUtils.Y_BORDER_STYLE, attr);
		handler.endElement("", "", FileUtils.Y_BORDER_STYLE);
	}

	private void addFillColor(TransformerHandler handler, GraphMLStyle style) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		attr.addAttribute("", "", "color", "CDATA", style.getFillColor());
		attr.addAttribute("", "", "transparent", "CDATA", "false");
		handler.startElement("", "", FileUtils.Y_FILL, attr);
		handler.endElement("", "", FileUtils.Y_FILL);
	}

	private void addOrientation(TransformerHandler handler, String szOrientation) throws SAXException {

		AttributesImpl attr = new AttributesImpl();

		attr.addAttribute("", "", "key", "CDATA", NODE_ORIENTATION_ATTR);
		handler.startElement("", "", "data", attr);

		if (null != szOrientation) {
			handler.characters(szOrientation.toCharArray(), 0, szOrientation.length());
		}
		handler.endElement("", "", "data");
	}

	private void addClone(TransformerHandler handler, Clone clone) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		attr.addAttribute("", "", "key", "CDATA", NODE_CLONE_ATTR);
		handler.startElement("", "", "data", attr);

		if (null != clone) {
			if (null != clone.getLabel()) {
				String cloneInfo = clone.getLabel().getText();
				handler.characters(cloneInfo.toCharArray(), 0, cloneInfo.length());
			}
		}
		handler.endElement("", "", "data");
	}

	private void addAnnotation(TransformerHandler handler, Extension extension) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		String szAnnotation = "";
		String szRDFUrl = "";
		String szRDFDescription = "";
		String szBqmodelIs = "";
		String szBiolIs = "";

		if (null != extension) {
			for (Element e : extension.getAny()) {

				if (null != e) {
					szAnnotation = szAnnotation.concat("xmlns:ns2=\"" + e.getAttribute("xmlns:ns2") + "\" xmlns=\""
							+ e.getAttribute("xmlns") + "\"\n");

					for (int i = 0; i < e.getElementsByTagName("rdf:RDF").getLength(); i++) {

						Element eRDFTag = (Element) e.getElementsByTagName("rdf:RDF").item(i);
						szRDFUrl = szRDFUrl.concat("xmlns:rdf=\"" + eRDFTag.getAttribute("xmlns:rdf") + "\" xmlns=\""
								+ eRDFTag.getAttribute("xmlns") + "\" xmlns:bqbiol=\""
								+ eRDFTag.getAttribute("xmlns:bqbiol") + "\" xmlns:bqmodel=\""
								+ eRDFTag.getAttribute("xmlns:bqmodel") + "\" xmlns:celldesigner=\""
								+ eRDFTag.getAttribute("xmlns:celldesigner") + "\" xmlns:dc=\""
								+ eRDFTag.getAttribute("xmlns:dc") + "\" xmlns:dcterms=\""
								+ eRDFTag.getAttribute("xmlns:dcterms") + "\" xmlns:vCard=\""
								+ eRDFTag.getAttribute("xmlns:vCard") + "\"");
					}

					for (int i = 0; i < e.getElementsByTagName("rdf:Description").getLength(); i++) {

						Element eRDFDEscription = (Element) e.getElementsByTagName("rdf:Description").item(i);
						szRDFDescription = szRDFDescription.concat(eRDFDEscription.getAttribute("rdf:about") + "\n");
					}

					for (int i = 0; i < e.getElementsByTagName("bqmodel:is").getLength(); i++) {
						Element e1 = (Element) e.getElementsByTagName("bqmodel:is").item(i);

						for (int j = 0; j < e1.getElementsByTagName("rdf:li").getLength(); j++) {
							Element e2 = (Element) e1.getElementsByTagName("rdf:li").item(j);
							if (null != e2) {
								szBqmodelIs = szBqmodelIs.concat(e2.getAttribute("rdf:resource") + "\n");
							}
						}
					}

					for (int i = 0; i < e.getElementsByTagName("bqbiol:is").getLength(); i++) {
						Element e1 = (Element) e.getElementsByTagName("bqbiol:is").item(i);
						if (null != e1) {

							for (int j = 0; j < e1.getElementsByTagName("rdf:li").getLength(); j++) {
								Element e2 = (Element) e1.getElementsByTagName("rdf:li").item(j);
								if (null != e2) {
									szBiolIs = szBiolIs.concat(e2.getAttribute("rdf:resource") + "\n");
								}
							}
						}
					}
				}
			}
		}

		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_ANNOTATIONS_ATTR);
		handler.startElement("", "", "data", attr);
		handler.characters(szAnnotation.toCharArray(), 0, szAnnotation.length());
		handler.endElement("", "", "data");

		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_BQMODELIS_ATTR);
		handler.startElement("", "", "data", attr);
		handler.characters(szBqmodelIs.toCharArray(), 0, szBqmodelIs.length());
		handler.endElement("", "", "data");

		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_BIOLIS_ATTR);
		handler.startElement("", "", "data", attr);
		handler.characters(szBiolIs.toCharArray(), 0, szBiolIs.length());
		handler.endElement("", "", "data");

		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_DESCRIPT_ATTR);
		handler.startElement("", "", "data", attr);
		handler.characters(szRDFDescription.toCharArray(), 0, szRDFDescription.length());
		handler.endElement("", "", "data");

		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_URL_ATTR);
		handler.startElement("", "", "data", attr);
		handler.characters(szRDFUrl.toCharArray(), 0, szRDFUrl.length());
		handler.endElement("", "", "data");
	}

	private void addNotes(TransformerHandler handler, Notes notes) throws SAXException {
		AttributesImpl attr = new AttributesImpl();

		attr.clear();
		attr.addAttribute("", "", "key", "CDATA", NODE_NOTES_ATTR);
		handler.startElement("", "", "data", attr);

		if (null != notes) {
			for (Element e : notes.getAny()) {
				String notesInfo = e.getTextContent();
				handler.characters(notesInfo.toCharArray(), 0, notesInfo.length());
			}
		}
		handler.endElement("", "", "data");
	}

	private Glyph findGlyph(String id) {
		for (Glyph g : map.getGlyph()) {
			if (g.getId().equals(id)) {
				return g;
			} else {
				for (Port p : g.getPort()) {
					if (p.getId().equals(id)) {
						return g;
					}
				}
			}
		}
		return null;
	}

	private Glyph findNode(String id) {
		for (Glyph g : graph.vertexSet()) {
			if (g.getId().equals(id)) {
				return g;
			} else {
				for (Port p : g.getPort()) {
					if (p.getId().equals(id)) {
						return g;
					}
				}
			}
		}

		return null;
	}
}
