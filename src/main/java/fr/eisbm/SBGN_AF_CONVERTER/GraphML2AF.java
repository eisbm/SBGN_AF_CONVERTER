package fr.eisbm.SBGN_AF_CONVERTER;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jgrapht.alg.util.Pair;
import org.sbgn.SbgnUtil;
import org.sbgn.bindings.Arc;
import org.sbgn.bindings.Arc.End;
import org.sbgn.bindings.Arc.Next;
import org.sbgn.bindings.Arc.Start;
import org.sbgn.bindings.Bbox;
import org.sbgn.bindings.Glyph;
import org.sbgn.bindings.Glyph.Clone;
import org.sbgn.bindings.Glyph.Entity;
import org.sbgn.bindings.Label;
import org.sbgn.bindings.Map;
import org.sbgn.bindings.SBGNBase.Extension;
import org.sbgn.bindings.SBGNBase.Notes;
import org.sbgn.bindings.Sbgn;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class GraphML2AF {

	private static final String LIST_OF_COLOR_DEFINITIONS_TAG = "listOfColorDefinitions";
	private static final String VALUE_ATTR = "value";
	private static final String ID_LIST_ATTR = "idList";
	private static final String ID_ATTR = "id";
	private static final String GRAPHICS_TAG = "g";
	private static final String FILL_ATTR = "fill";
	private static final String STROKE_WIDTH_ATTR = "strokeWidth";
	private static final String STROKE_ATTR = "stroke";
	private static final String STYLE_TAG = "style";
	private static final String COLOR_DEFINITION_TAG = "colorDefinition";
	private static final String LIST_OF_STYLES_TAG = "listOfStyles";

	private static final String COLOR_PREFIX = "color_";
	private static final String STYLE_PREFIX = "style_";
	private static final String COLOR_ATTR = "color";
	private static final String HEIGHT_ATTR = "height";
	private static final String WIDTH_ATTR = "width";
	private static final String FONTSIZE_ATTR = "fontSize";
	private static final String X_POS_ATTR = "x";
	private static final String Y_POS_ATTR = "y";
	private static final String ICON_DATA_ATTR = "iconData";

	private static final String XMLNS_N2_NS = "xmlns:ns2";
	private static final String XMLNS_NS = "xmlns";
	private static final String XMLNS_BQBIOL_NS = "xmlns:bqbiol";
	private static final String XMLNS_BQMODEL_NS = "xmlns:bqmodel";
	private static final String XMLNS_CELL_DESIGNER_NS = "xmlns:celldesigner";
	private static final String XMLNS_DC_NS = "xmlns:dc";
	private static final String XMLNS_DC_TERMS_NS = "xmlns:dcterms";
	private static final String XMLNS_VCARD_NS = "xmlns:vCard";

	private static final String ANNOTATION_TAG = "annotation";
	private static final String KEY_TAG = "key";
	private static final String NOTES_TAG = "notes";
	private static final String CLONE_TAG = "clone";
	private static final String ORIENTATION_TAG = "orientation";
	private static final String BQMODEL_IS_GRAPHML_TAG = "bqmodel_is";
	private static final String BIOL_IS_GRAPHML_TAG = "biol_is";
	private static final String BQMODEL_IS_TAG = "bqmodel:is";
	private static final String BQBIOL_IS_TAG = "bqbiol:is";
	private static final String NODE_TAG = "node";
	private static final String EDGE_TAG = "edge";
	private static final String DATA_TAG = "data";
	private static final String RDF_LI_TAG = "rdf:li";
	private static final String URL_TAG = "url";
	private static final String RDF_RESOURCE_TAG = "rdf:resource";
	private static final String RDF_BAG_TAG = "rdf:Bag";
	private static final String RDF_RDF_TAG = "rdf:RDF";
	private static final String RDF_ABOUT_TAG = "rdf:about";
	private static final String RDF_DESCRIPTION_TAG = "rdf:Description";

	Sbgn sbgn = new Sbgn();
	Map map = new Map();
	List<Pair<String, String>> resourceList = new ArrayList<Pair<String, String>>();
	Set<String> colorSet = new HashSet<String>();
	java.util.Map<String, String> colorMap = new HashMap<String, String>();
	java.util.Map<String, SBGNMLStyle> styleMap = new HashMap<String, SBGNMLStyle>();
	java.util.Map<String, String> compoundComplexMap = new HashMap<String, String>();
	java.util.Map<String, String> compoundCompartmentMap = new HashMap<String, String>();
	Set<String> complexSet = new HashSet<String>();
	Set<String> compartmentSet = new HashSet<String>();

	public static void main(String[] args) {

		convert(FileUtils.OUT_YED_FILE);
		System.out.println("simulation finished");
	}

	public static void convert(String szInputFileName) {
		GraphML2AF gs = new GraphML2AF();
		String szOutSBGNFile = szInputFileName.replace(".graphml", "").concat(".sbgn");
		boolean bConversion = gs.parseGraphMLFile(szInputFileName, szOutSBGNFile);

		if (bConversion) {
			String szSBGNv02File = szInputFileName.replace(".graphml", "").concat("-SBGNv02.sbgn");
			// make a SBGN (v02) -valid file
			gs.createSBGNv02File(szOutSBGNFile, szSBGNv02File);
		}
	}

	boolean parseGraphMLFile(String szInGraphMLFileName, String szOutSBGNFile) {
		boolean bConversion = false;
		try {
			File inputFile = new File(szInGraphMLFileName);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(inputFile);
			doc.getDocumentElement().normalize();

			File outputFile = new File(szOutSBGNFile);

			map.setLanguage("activity flow");
			sbgn.setMap(map);

			String szNotesTagId = "";
			String szCloneTagId = "";
			String szBqmodelIsTagId = "";
			String szBqbiolIsTagId = "";
			String szAnnotationTagId = "";
			String szNodeURLTagId = "";
			String szOrientationTagId = "";
			NodeList nKeyList = doc.getElementsByTagName(KEY_TAG);
			for (int temp = 0; temp < nKeyList.getLength(); temp++) {
				Node nNode = nKeyList.item(temp);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					if (eElement.getAttribute("attr.name").toLowerCase().equals(NOTES_TAG)) {
						szNotesTagId = eElement.getAttribute(ID_ATTR);
					} else if (eElement.getAttribute("attr.name").toLowerCase().equals(CLONE_TAG)) {
						szCloneTagId = eElement.getAttribute(ID_ATTR);
					} else if (eElement.getAttribute("attr.name").toLowerCase().equals(ANNOTATION_TAG)) {
						szAnnotationTagId = eElement.getAttribute(ID_ATTR);
					} else if (eElement.getAttribute("attr.name").toLowerCase().equals(BQMODEL_IS_GRAPHML_TAG)) {
						szBqmodelIsTagId = eElement.getAttribute(ID_ATTR);
					} else if (eElement.getAttribute("attr.name").toLowerCase().equals(BIOL_IS_GRAPHML_TAG)) {
						szBqbiolIsTagId = eElement.getAttribute(ID_ATTR);
					} else if (eElement.getAttribute("attr.name").toLowerCase().equals(ORIENTATION_TAG)) {
						szOrientationTagId = eElement.getAttribute(ID_ATTR);
					} else if ((eElement.getAttribute("attr.name").toLowerCase().equals(URL_TAG))
							&& (eElement.getAttribute("for").toLowerCase().equals(NODE_TAG))) {
						szNodeURLTagId = eElement.getAttribute(ID_ATTR);
					}
				}
			}

			// complexes and compartments are mapped by yEd groups
			NodeList nComplexList = doc.getElementsByTagName(NODE_TAG);

			for (int temp = 0; temp < nComplexList.getLength(); temp++) {
				Node nNode = nComplexList.item(temp);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;

					// compartment - mapped by yEd groups with the <y:GroupNode> tag
					NodeList _nlGroupList = eElement.getElementsByTagName(FileUtils.Y_GROUP_NODE);
					if (_nlGroupList.getLength() > 0) {
						String szCompartmentId = eElement.getAttribute(ID_ATTR);
						Glyph _compartmentGlyph = new Glyph();
						_compartmentGlyph.setId(szCompartmentId);
						_compartmentGlyph.setClazz(FileUtils.SBGN_COMPARTMENT);

						NodeList _nlNodeLabelList = eElement.getElementsByTagName(FileUtils.Y_NODE_LABEL);
						String szTextContent = _nlNodeLabelList.item(0).getTextContent().trim();

						if (!szTextContent.equals("")) { // setting the label of the complex e.g. cytosolic proteasome..
							Label _label = new Label();
							_label.setText(szTextContent);
							_compartmentGlyph.setLabel(_label);
						}

						// setting the bbox info setBbox(_compartmentGlyph,
						eElement.getElementsByTagName(FileUtils.Y_GEOMETRY);

						// setting the bbox info
						setBbox(_compartmentGlyph, eElement.getElementsByTagName(FileUtils.Y_GEOMETRY));

						// setting style info
						setStyle(eElement, szCompartmentId);

						NodeList nCompoundList = eElement.getElementsByTagName(NODE_TAG);
						for (int tempCompound = 0; tempCompound < nCompoundList.getLength(); tempCompound++) {
							Node nCompoundNode = nCompoundList.item(tempCompound);

							if (nCompoundNode.getNodeType() == Node.ELEMENT_NODE) {
								Element eCompoundElement = (Element) nCompoundNode;
								String szCompoundId = eCompoundElement.getAttribute(ID_ATTR);
								compoundCompartmentMap.put(szCompoundId, szCompartmentId);
							}
						}
						compartmentSet.add(szCompartmentId);

						// add the glyph to the map
						map.getGlyph().add(_compartmentGlyph);
					}

					else {
						// checking the node type
						NodeList _nlConfigList = eElement.getElementsByTagName(FileUtils.Y_GENERIC_GROUP_NODE);
						if (_nlConfigList.getLength() > 0) {
							if (((Element) _nlConfigList.item(0)).hasAttribute("configuration")) {
								String szYEDNodeType = ((Element) _nlConfigList.item(0)).getAttribute("configuration");
								if (szYEDNodeType.equals(FileUtils.COM_YWORKS_SBGN_COMPLEX)) {
									String szComplexId = eElement.getAttribute(ID_ATTR);
									Glyph _complexGlyph = new Glyph();
									_complexGlyph.setId(szComplexId);
									String szGlyphClass = parseYedNodeType(szYEDNodeType);
									_complexGlyph.setClazz(szGlyphClass);

									NodeList _nlNodeLabelList = eElement.getElementsByTagName(FileUtils.Y_NODE_LABEL);
									String szTextContent = _nlNodeLabelList.item(0).getTextContent().trim();

									if (!szTextContent.equals("")) {
										// setting the label of the complex e.g. cytosolic proteasome..
										Label _label = new Label();
										_label.setText(szTextContent);
										_complexGlyph.setLabel(_label);
									}

									NodeList nCompoundList = eElement.getElementsByTagName(NODE_TAG);
									// setting the bbox info
									setBbox(_complexGlyph, eElement.getElementsByTagName(FileUtils.Y_GEOMETRY));

									// setting style info
									setStyle(eElement, szComplexId);

									for (int tempCompound = 0; tempCompound < nCompoundList
											.getLength(); tempCompound++) {
										Node nCompoundNode = nCompoundList.item(tempCompound);

										if (nCompoundNode.getNodeType() == Node.ELEMENT_NODE) {
											Element eCompoundElement = (Element) nCompoundNode;
											String szCompoundId = eCompoundElement.getAttribute(ID_ATTR);

											Glyph _glyph = parseGlyphInfo(doc, szNotesTagId, szCloneTagId,
													szBqmodelIsTagId, szBqbiolIsTagId, szAnnotationTagId,
													szNodeURLTagId, szOrientationTagId, eCompoundElement, szCompoundId);

											_complexGlyph.getGlyph().add(_glyph);

											compoundComplexMap.put(szCompoundId, szComplexId);
										}
									}

									complexSet.add(szComplexId);

									setCompartmentReference(_complexGlyph);

									// add the glyph to the map
									map.getGlyph().add(_complexGlyph);
								}
							}
						}
					}
				}
			}

			// nodes:
			NodeList nList = doc.getElementsByTagName(NODE_TAG);

			for (int temp = 0; temp < nList.getLength(); temp++) {
				Node nNode = nList.item(temp);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;

					// get id of the node/glyph
					String szGlyphId = eElement.getAttribute(ID_ATTR);

					if ((!compoundComplexMap.containsKey(szGlyphId)) && (!complexSet.contains(szGlyphId))
							&& (!compartmentSet.contains(szGlyphId))) {
						Glyph _glyph = parseGlyphInfo(doc, szNotesTagId, szCloneTagId, szBqmodelIsTagId,
								szBqbiolIsTagId, szAnnotationTagId, szNodeURLTagId, szOrientationTagId, eElement,
								szGlyphId);

						setCompartmentReference(_glyph);

						// add the glyph to the map
						map.getGlyph().add(_glyph);
					}
				}

			}

			// edges/arcs:
			NodeList nEdgeList = doc.getElementsByTagName(EDGE_TAG);

			for (int temp = 0; temp < nEdgeList.getLength(); temp++) {
				Node nEdge = nEdgeList.item(temp);
				Arc _arc = new Arc();

				if (nEdge.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nEdge;
					String szArrowDirection = processNodeList(eElement.getElementsByTagName(FileUtils.Y_ARROWS));
					String szArcType = FileUtils.SBGN_EQUIVALENCE_ARC;
					boolean bEdgeToBeCorrected = false;

					if (szArrowDirection.contains("white_delta_bar")) {
						szArcType = FileUtils.SBGN_NECESSARY_STIMULATION;
						if (szArrowDirection.contains("source=\"white_delta_bar\"")) {
							bEdgeToBeCorrected = true;
						}
					} else if (szArrowDirection.contains("white_diamond")) {
						szArcType = FileUtils.SBGN_UNKNOWN_INFLUENCE;
						if (szArrowDirection.contains("source=\"white_diamond\"")) {
							bEdgeToBeCorrected = true;
						}
					} else if (szArrowDirection.contains("t_shape")) {
						szArcType = FileUtils.SBGN_NEGATIVE_INFLUENCE;
						if (szArrowDirection.contains("source=\"t_shape\"")) {
							bEdgeToBeCorrected = true;
						}
					} else if (szArrowDirection.contains("white_delta")) {
						szArcType = FileUtils.SBGN_POSITIVE_INFLUENCE;
						if (szArrowDirection.contains("source=\"white_delta\"")) {
							bEdgeToBeCorrected = true;
						}
					}

					_arc.setClazz(szArcType);

					// get id of the edge/arc
					String szArcAttributes = getElementAttributes(eElement).trim();

					String delims = "[\t]";
					String szArcId = "", szArcSource = "", szArcTarget = "";
					szArcAttributes = szArcAttributes.replaceAll("\"", "");
					String[] tokens = szArcAttributes.split(delims);
					float fStartX = 0, fStartY = 0, fStartH = 0, fStartW = 0;
					float fTargetX = 0, fTargetY = 0, fTargetH = 0, fTargetW = 0;

					for (int i = 0; i < tokens.length; i++) {
						if (tokens[i].contains("id=")) {
							szArcId = tokens[i].replaceAll("id=", "");
							_arc.setId(szArcId);
						} else if (tokens[i].contains("source=")) {
							szArcSource = tokens[i].replaceAll("source=", "");
							for (Glyph g : map.getGlyph()) {
								if (g.getId().equals(szArcSource)) {
									if (bEdgeToBeCorrected) {
										_arc.setTarget(g);
									} else {
										_arc.setSource(g);
									}

									if (null != g.getBbox()) {
										fStartX = g.getBbox().getX();
										fStartY = g.getBbox().getY();
										fStartH = g.getBbox().getH();
										fStartW = g.getBbox().getW();
									}
									break;
								}
							}
						} else if (tokens[i].contains("target=")) {
							szArcTarget = tokens[i].replaceAll("target=", "");
							for (Glyph g : map.getGlyph()) {
								if (g.getId().equals(szArcTarget)) {
									if (bEdgeToBeCorrected) {
										_arc.setSource(g);
									} else {
										_arc.setTarget(g);
									}

									if (null != g.getBbox()) {
										fTargetX = g.getBbox().getX();
										fTargetY = g.getBbox().getY();
										fTargetH = g.getBbox().getH();
										fTargetW = g.getBbox().getW();
									}
									break;
								}
							}
						}
					}

					if ((_arc.getSource() != null) && (_arc.getTarget() != null)) {

						if (((Glyph) _arc.getSource()).getClazz().toUpperCase().equals("OR")
								|| ((Glyph) _arc.getTarget()).getClazz().toUpperCase().equals("OR")
								|| (((Glyph) _arc.getSource()).getClazz().toUpperCase().equals("AND")
								|| ((Glyph) _arc.getTarget()).getClazz().toUpperCase().equals("AND"))
								|| (((Glyph) _arc.getSource()).getClazz().toUpperCase().equals("NOT")
								|| ((Glyph) _arc.getTarget()).getClazz().toUpperCase().equals("NOT"))) {
							_arc.setClazz(FileUtils.SBGN_LOGIC_ARC);
						}
					}

					String delimsCoord = "[\t]";
					String szPathCoordinates = processNodeList(eElement.getElementsByTagName(FileUtils.Y_PATH));
					szPathCoordinates = szPathCoordinates.replaceAll("\"", "");
					String[] tokensCoordinates = szPathCoordinates.split(delimsCoord);
					if (tokensCoordinates.length == 4) {
						Start _start = new Start();
						String szSX = tokensCoordinates[0].replaceAll("sx=", "");
						_start.setX(Float.parseFloat(szSX) + fStartX + fStartW / 2);
						// _start.setX(Float.parseFloat(szSX));

						// System.out.println(szSX+"\t"+fStartX+"\t"+_start.getX());
						String szSY = tokensCoordinates[1].replaceAll("sy=", "");
						_start.setY(Float.parseFloat(szSY) + fStartY + fStartH / 2);
						// _start.setY(Float.parseFloat(szSY) );

						_arc.setStart(_start);

						End _end = new End();
						String szTX = tokensCoordinates[2].replaceAll("tx=", "");
						_end.setX(Float.parseFloat(szTX) + fTargetX + fTargetW / 2);
						// _end.setX(Float.parseFloat(szTX));
						String szTY = tokensCoordinates[3].replaceAll("ty=", "");
						_end.setY(Float.parseFloat(szTY) + fTargetY + fTargetH / 2);
						// _end.setY(Float.parseFloat(szTY));
						_arc.setEnd(_end);
					}

					String szPointInfo = processNodeList(eElement.getElementsByTagName(FileUtils.Y_POINT));
					if (!szPointInfo.isEmpty()) {
						String szPointXCoord = "", szPointYCoord = "";
						szPointInfo = szPointInfo.replaceAll("\"", "");
						String[] tokensPort = szPointInfo.split(delims);

						for (int i = 0; i < tokensPort.length - 1; i += 2) {
							if (tokensPort[i].contains("x=")) {
								szPointXCoord = tokensPort[i].replaceAll("x=", "");
							}
							if (tokensPort[i + 1].contains("y=")) {
								szPointYCoord = tokensPort[i + 1].replaceAll("y=", "");
							}

							Next _next = new Next();
							_next.setX(Float.parseFloat(szPointXCoord));
							_next.setY(Float.parseFloat(szPointYCoord));
							_arc.getNext().add(_next);
						}
					}

					NodeList nlLineStyle = eElement.getElementsByTagName(FileUtils.Y_LINE_STYLE);
					// getting the border color info
					String szStrokeColorId = ((Element) (nlLineStyle.item(0))).getAttribute(COLOR_ATTR);
					colorSet.add(szStrokeColorId);

					// getting the stroke width info
					float fStrokeWidth = Float.parseFloat(((Element) (nlLineStyle.item(0))).getAttribute(WIDTH_ATTR));

					String szStyleId = STYLE_PREFIX + fStrokeWidth + szStrokeColorId.replaceFirst("#", "");

					if (!styleMap.containsKey(szStyleId)) {
						styleMap.put(szStyleId, new SBGNMLStyle(szStyleId, szStrokeColorId, fStrokeWidth));
					}
					styleMap.get(szStyleId).addElementIdToSet(eElement.getAttribute(ID_ATTR));

					NodeList nlCardinalityList = eElement.getElementsByTagName(FileUtils.Y_EDGE_LABEL);
					if (nlCardinalityList.getLength() > 0) {
						String szCardinality = nlCardinalityList.item(0).getTextContent().trim();
						if (!szCardinality.equals("")) {
							Glyph cardGlyph = new Glyph();
							cardGlyph.setClazz(FileUtils.SBGN_CARDINALITY);
							Label _label = new Label();
							_label.setText(szCardinality);
							cardGlyph.setLabel(_label);
							_arc.getGlyph().add(cardGlyph);
						}
					}
				}

				// add the arc to the map
				map.getArc().add(_arc);
			}

			// resources:
			NodeList nResourceList = doc.getElementsByTagName(FileUtils.Y_RESOURCE);

			for (int temp = 0; temp < nResourceList.getLength(); temp++) {
				Node nResource = nResourceList.item(temp);

				if (nResource.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nResource;
					Glyph _glyph = new Glyph();

					// get id of the resources
					String szResourceId = eElement.getAttribute(ID_ATTR);
					_glyph.setId(szResourceId);

					NodeList _nlGenericNodeList = eElement.getElementsByTagName(FileUtils.Y_GENERIC_NODE);
					NodeList _nlShapeNodeList = eElement.getElementsByTagName(FileUtils.Y_SHAPE_NODE);

					if (_nlGenericNodeList.getLength() > 0) {

						String szYEDNodeType = ((Element) _nlGenericNodeList.item(0)).getAttribute("configuration");

						// activity of a metabolite
						if (szYEDNodeType.contains(FileUtils.COM_YWORKS_SBGN_SIMPLE_CHEMICAL)) {
							_glyph.setClazz(FileUtils.SBGN_UNIT_OF_INFORMATION);
							_glyph.setId(_glyph.getId() + "_" + _glyph.getGlyph().size());

							Entity _entityValue = new Entity();
							_entityValue.setName(FileUtils.SBGN_SIMPLE_CHEMICAL);
							_glyph.setEntity(_entityValue);
						} else {
							_glyph.setClazz(parseYedNodeType(szYEDNodeType));
						}

					} else if (_nlShapeNodeList.getLength() > 0) {

						_glyph.setClazz(FileUtils.SBGN_UNIT_OF_INFORMATION);
					}

					NodeList _nlNodeLabelList = eElement.getElementsByTagName(FileUtils.Y_NODE_LABEL);
					if (_nlNodeLabelList.getLength() > 0) {
						String szNodeText = _nlNodeLabelList.item(0).getTextContent().trim();

						if (!szNodeText.equals("")) {

							if (_glyph.getClazz().equals(FileUtils.SBGN_STATE_VARIABLE)) {
								// setting the label of the glyph e.g. P, 2P..
								Glyph.State _state = new Glyph.State();
								int iDelimPos = szNodeText.indexOf("@");
								String szValue = "";
								String szVariable = "";

								if (iDelimPos < 0) {
									szValue = szNodeText;
								} else if (0 == iDelimPos) {
									szVariable = szNodeText.substring(iDelimPos + 1, szNodeText.length());
								} else if (iDelimPos > 0) {
									szValue = szNodeText.substring(0, iDelimPos);
									szVariable = szNodeText.substring(iDelimPos + 1, szNodeText.length());
								}

								_state.setValue(szValue);
								_state.setVariable(szVariable);
								_glyph.setState(_state);
							} else if (_glyph.getClazz().equals(FileUtils.SBGN_UNIT_OF_INFORMATION)) {
								Label _label = new Label();
								_label.setText(szNodeText);
								_glyph.setLabel(_label);
							}
						}
					}

					// setting the bbox info
					setBbox(_glyph, eElement.getElementsByTagName(FileUtils.Y_GEOMETRY));

					// add the state variable to the corresponding glyph
					for (Pair<String, String> _pair : resourceList) {
						if (_pair.first.equals(szResourceId)) {
							String szParentGlyphId = _pair.second;
							findGlyphParent(szParentGlyphId, _glyph, map.getGlyph());
						}
					}
				}
			}

			addExtension(doc);

			// write everything to disk
			SbgnUtil.writeToFile(sbgn, outputFile);

			System.out.println(
					"SBGN file validation: " + (SbgnUtil.isValid(outputFile) ? "validates" : "does not validate"));

			bConversion = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bConversion;
	}

	private void findGlyphParent(String szParentGlyphId, Glyph _childGlyph, List<Glyph> _listOfGLyphs) {
		for (Glyph _parentGlyph : _listOfGLyphs) {
			if (_parentGlyph.getId().equals(szParentGlyphId)) {
				_parentGlyph.getGlyph().add(_childGlyph);
			} else {
				findGlyphParent(szParentGlyphId, _childGlyph, _parentGlyph.getGlyph());
			}
		}
	}

	private void setCompartmentReference(Glyph _glyph) {
		String szGlyphId = _glyph.getId();
		if (compoundCompartmentMap.containsKey(szGlyphId)) {
			String szCompartmentId = compoundCompartmentMap.get(szGlyphId);

			// searching for the compartment with the id equal to szCompartmentId
			for (Glyph _compartment : map.getGlyph()) {
				if (_compartment.getId().equals(szCompartmentId)) {
					_glyph.setCompartmentRef(_compartment);
					break;
				}
			}
		}

	}

	private void setStyle(Element eElement, String szId) {
		String szFillColorId = ((Element) (eElement.getElementsByTagName(FileUtils.Y_FILL).item(0)))
				.getAttribute(COLOR_ATTR);
		colorSet.add(szFillColorId);

		NodeList nlBorderStyle = eElement.getElementsByTagName(FileUtils.Y_BORDER_STYLE);
		// getting the border color info
		String szStrokeColorId = ((Element) (nlBorderStyle.item(0))).getAttribute(COLOR_ATTR);
		colorSet.add(szStrokeColorId);

		// getting the stroke width color info
		float fStrokeWidth = Float.parseFloat(((Element) (nlBorderStyle.item(0))).getAttribute(WIDTH_ATTR));

		// getting the stroke width color info
		String szStrokeColorWidth = ((Element) (eElement.getElementsByTagName(FileUtils.Y_NODE_LABEL).item(0)))
				.getAttribute(FONTSIZE_ATTR);
		float fFontSize = FileUtils.DEFAULT_FONT_SIZE;
		if (!szStrokeColorWidth.equals("")) {
			fFontSize = Float.parseFloat(szStrokeColorWidth);
		}

		String szStyleId = STYLE_PREFIX + fStrokeWidth + szFillColorId.replaceFirst("#", "") + fFontSize
				+ szStrokeColorId.replaceFirst("#", "");
		if (!styleMap.containsKey(szStyleId)) {
			styleMap.put(szStyleId,
					new SBGNMLStyle(szStyleId, szFillColorId, szStrokeColorId, fStrokeWidth, fFontSize));
		}
		styleMap.get(szStyleId).addElementIdToSet(szId);
	}

	private Glyph parseGlyphInfo(Document doc, String szNotesTagId, String szCloneTagId, String szBqmodelIsTagId,
			String szBqbiolIsTagId, String szAnnotationTagId, String szNodeURLTagId, String szOrientationTagId,
			Element eElement, String szGlyphId) {
		Glyph _glyph = new Glyph();
		_glyph.setId(szGlyphId);

		// setting the bbox info
		setBbox(_glyph, eElement.getElementsByTagName(FileUtils.Y_GEOMETRY));

		// setting the glyph class
		NodeList _nlConfigList = eElement.getElementsByTagName(FileUtils.Y_GENERIC_NODE);
		NodeList _nlShapeNodeList = eElement.getElementsByTagName(FileUtils.Y_SHAPE_NODE);
		NodeList _nlNodeLabelList = eElement.getElementsByTagName(FileUtils.Y_NODE_LABEL);

		if (_nlConfigList.getLength() > 0) {
			String szYEDNodeType = ((Element) _nlConfigList.item(0)).getAttribute("configuration");

			// activity of a metabolite
			if (szYEDNodeType.contains(FileUtils.COM_YWORKS_SBGN_SIMPLE_CHEMICAL)) {
				_glyph.setClazz(FileUtils.SBGN_BIOLOGICAL_ACTIVITY);
				setMetaboliteBiologicalActivity(_glyph);
			} else {
				_glyph.setClazz(parseYedNodeType(szYEDNodeType));
			}

		} else if (_nlShapeNodeList.getLength() > 0) {
			_glyph.setClazz(FileUtils.SBGN_BIOLOGICAL_ACTIVITY);
		}

		String szTextContent = _nlNodeLabelList.item(0).getTextContent().trim();
		if (!_glyph.getClazz().equals("operator")) {

			if (!szTextContent.equals("")) {
				// setting the label of the glyph e.g. Coenzyme A..
				Label _label = new Label();
				_label.setText(szTextContent);
				_glyph.setLabel(_label);
			}
		} else {
			_glyph.setClazz(szTextContent.toLowerCase());
		}

		// the glyph has resouces (i.e. state variables, multimer states etc.)
		if (_nlNodeLabelList.getLength() > 1) {
			for (int i = 1; i < _nlNodeLabelList.getLength(); i++) {
				Element _element = (Element) _nlNodeLabelList.item(i);
				Pair pair = new Pair(_element.getAttribute(ICON_DATA_ATTR), szGlyphId);
				if (!resourceList.contains(pair)) {
					resourceList.add(pair);
				}
			}
		}

		// setting style info
		setStyle(eElement, szGlyphId);

		// parse data information on notes, annotation, orientation, clone etc.
		NodeList nlDataList = eElement.getElementsByTagName(DATA_TAG);

		Element eltAnnotation = doc.createElement(ANNOTATION_TAG);
		// TODO: to read the namespace from the file
		Element rdfRDF = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", RDF_RDF_TAG);
		eltAnnotation.appendChild(rdfRDF);
		Element rdfDescription = doc.createElement(RDF_DESCRIPTION_TAG);
		rdfRDF.appendChild(rdfDescription);
		rdfDescription.setAttribute(RDF_ABOUT_TAG, "#" + _glyph.getId());

		for (int temp2 = 0; temp2 < nlDataList.getLength(); temp2++) {
			Element _element = ((Element) (nlDataList.item(temp2)));

			// parse notes information

			if (_element.getAttribute(KEY_TAG).equals(szNotesTagId)) {
				_glyph.setNotes(getSBGNNotes(_element));
			}

			// parse annotation information
			if (_element.getAttribute(KEY_TAG).equals(szAnnotationTagId)) {
				String szText = _element.getTextContent();
				if (!szText.equals("")) {
					szText = szText.replaceAll("\"", "");
					String delims = " ";
					String[] tokens = szText.split(delims);
					for (int i = 0; i < tokens.length; i++) {
						String value = tokens[i].substring(tokens[i].indexOf("=") + 1);
						if (tokens[i].contains(XMLNS_N2_NS + "=")) {
							eltAnnotation.setAttribute(XMLNS_N2_NS, value);
						} else if (tokens[i].contains(XMLNS_NS + "=")) {
							eltAnnotation.setAttribute(XMLNS_NS, value);
						}
					}
				}
			}

			// parse namespace information
			else if (_element.getAttribute(KEY_TAG).equals(szNodeURLTagId)) {
				String szText = _element.getTextContent();
				if (!szText.equals("")) {
					szText = szText.replaceAll("\"", "");
					String delims = " ";
					String[] tokens = szText.split(delims);
					for (int i = 0; i < tokens.length; i++) {
						String value = tokens[i].substring(tokens[i].indexOf("=") + 1);
						if (!value.equals("")) {

							if (tokens[i].contains(XMLNS_NS + "=")) {
								rdfRDF.setAttribute(XMLNS_NS, value);
							} else if (tokens[i].contains(XMLNS_BQBIOL_NS + "=")) {
								rdfRDF.setAttribute(XMLNS_BQBIOL_NS, value);
							} else if (tokens[i].contains(XMLNS_BQMODEL_NS + "=")) {
								rdfRDF.setAttribute(XMLNS_BQMODEL_NS, value);
							} else if (tokens[i].contains(XMLNS_CELL_DESIGNER_NS + "=")) {
								rdfRDF.setAttribute(XMLNS_CELL_DESIGNER_NS, value);
							} else if (tokens[i].contains(XMLNS_DC_NS + "=")) {
								rdfRDF.setAttribute(XMLNS_DC_NS, value);
							} else if (tokens[i].contains(XMLNS_DC_TERMS_NS + "=")) {
								rdfRDF.setAttribute(XMLNS_DC_TERMS_NS, value);
							} else if (tokens[i].contains(XMLNS_VCARD_NS + "=")) {
								rdfRDF.setAttribute(XMLNS_VCARD_NS, value);
							}
						}
					}
				}
			}

			// parse clone information
			else if (_element.getAttribute(KEY_TAG).equals(szCloneTagId)) {
				if (!_element.getTextContent().equals("")) {
					Label _label = new Label();
					_label.setText(_element.getTextContent());
					Clone _clone = new Clone();
					_clone.setLabel(_label);
					_glyph.setClone(_clone);
				}
			}

			// parse bqmodel:is information
			else if (_element.getAttribute(KEY_TAG).equals(szBqmodelIsTagId)) {
				String szText = _element.getTextContent();
				if (!szText.equals("")) {
					String delimsLine = "[\n]";
					String[] tokens = szText.split(delimsLine);
					for (int i = 0; i < tokens.length; i++) {
						Element elBqtModelIs = doc.createElement(BQMODEL_IS_TAG);
						// add rdf:Bag
						Element eltRDFBag = doc.createElement(RDF_BAG_TAG);
						elBqtModelIs.appendChild(eltRDFBag);
						Element eltRDFLi = doc.createElement(RDF_LI_TAG);
						eltRDFLi.setAttribute(RDF_RESOURCE_TAG, tokens[i]);
						eltRDFBag.appendChild(eltRDFLi);
						rdfDescription.appendChild(elBqtModelIs);
					}
				}
			}

			// parse bqbiol:is information
			else if (_element.getAttribute(KEY_TAG).equals(szBqbiolIsTagId)) {
				String szText = _element.getTextContent();
				if (!szText.equals("")) {
					Element eltBqbiolIs = doc.createElement(BQBIOL_IS_TAG);
					// add rdf:Bag
					Element eltRDFBag = doc.createElement(RDF_BAG_TAG);
					eltBqbiolIs.appendChild(eltRDFBag);

					String delimsLine = "[\n]";
					String[] tokens = szText.split(delimsLine);
					for (int i = 0; i < tokens.length; i++) {
						Element eltRDFLi = doc.createElement(RDF_LI_TAG);
						eltRDFLi.setAttribute(RDF_RESOURCE_TAG, tokens[i]);
						eltRDFBag.appendChild(eltRDFLi);
					}
					rdfDescription.appendChild(eltBqbiolIs);
				}
			}
		}

		Extension _extension = new Extension();
		_extension.getAny().add(eltAnnotation);
		_glyph.setExtension(_extension);

		return _glyph;
	}

	private void setMetaboliteBiologicalActivity(Glyph _glyph) {
		Glyph _childGlyph = new Glyph();
		_childGlyph.setClazz(FileUtils.SBGN_UNIT_OF_INFORMATION);
		_childGlyph.setId(_glyph.getId() + "_" + _glyph.getGlyph().size());

		Entity _entityValue = new Entity();
		_entityValue.setName(FileUtils.SBGN_SIMPLE_CHEMICAL);
		_childGlyph.setEntity(_entityValue);
		Bbox _bboxValue = new Bbox();
		_bboxValue.setX(_glyph.getBbox().getX() + 50);
		_bboxValue.setY(_glyph.getBbox().getY() - 10);
		_bboxValue.setH(20);
		_bboxValue.setW(20);
		_childGlyph.setBbox(_bboxValue);
		_glyph.getGlyph().add(_childGlyph);
	}

	private void setBbox(Glyph _glyph, NodeList nlGeometry) {
		Bbox bbox = new Bbox();

		String szHeight = ((Element) (nlGeometry.item(0))).getAttribute(HEIGHT_ATTR);
		String szWidth = ((Element) (nlGeometry.item(0))).getAttribute(WIDTH_ATTR);
		String szXPos = ((Element) (nlGeometry.item(0))).getAttribute(X_POS_ATTR);
		String szYPos = ((Element) (nlGeometry.item(0))).getAttribute(Y_POS_ATTR);

		bbox.setH(Float.parseFloat(szHeight));
		bbox.setW(Float.parseFloat(szWidth));
		bbox.setX(Float.parseFloat(szXPos));
		bbox.setY(Float.parseFloat(szYPos));
		_glyph.setBbox(bbox);
	}

	public Notes getSBGNNotes(Element notes) {
		Notes newNotes = new Notes();
		if (notes != null) {
			newNotes.getAny().add(notes);
			return newNotes;
		}
		return null;
	}

	private void addExtension(Document doc) {
		// add extension data
		Extension ext = new Extension();
		// add render information tag
		Element eltRenderInfo = doc.createElementNS("http://www.sbml.org/sbml/level3/version1/render/version1",
				"renderInformation");
		eltRenderInfo.setAttribute(ID_ATTR, "renderInformation");
		eltRenderInfo.setAttribute("backgroundColor", "#ffffff");
		eltRenderInfo.setAttribute("programName", "graphml2sbgn");
		eltRenderInfo.setAttribute("programVersion", "0.1");

		// add list of colors
		Element eltListOfColor = doc.createElement(LIST_OF_COLOR_DEFINITIONS_TAG);
		eltRenderInfo.appendChild(eltListOfColor);

		int i = 0;
		for (String _color : colorSet) {
			i++;
			colorMap.put(_color, COLOR_PREFIX + i);
		}

		for (Entry<String, String> e : colorMap.entrySet()) {
			Element eltColorId = doc.createElement(COLOR_DEFINITION_TAG);
			eltColorId.setAttribute(ID_ATTR, e.getValue());
			eltColorId.setAttribute(VALUE_ATTR, e.getKey());
			eltListOfColor.appendChild(eltColorId);
		}

		// add list of styles
		Element eltListOfStyles = doc.createElement(LIST_OF_STYLES_TAG);
		eltRenderInfo.appendChild(eltListOfStyles);
		for (Entry<String, SBGNMLStyle> e : styleMap.entrySet()) {
			Element eltStyleId = doc.createElement(STYLE_TAG);
			eltStyleId.setAttribute(ID_ATTR, e.getKey());
			eltStyleId.setAttribute(ID_LIST_ATTR, e.getValue().getElementSet());

			// add graphics of the style
			Element graphics = doc.createElement(GRAPHICS_TAG);
			graphics.setAttribute(FILL_ATTR, colorMap.get(e.getValue().getFillColor()));
			graphics.setAttribute(FONTSIZE_ATTR, Float.toString(e.getValue().getFontSize()));
			graphics.setAttribute(STROKE_ATTR, colorMap.get(e.getValue().getStrokeColor()));
			graphics.setAttribute(STROKE_WIDTH_ATTR, Float.toString(e.getValue().getStrokeWidth()));
			eltStyleId.appendChild(graphics);

			eltListOfStyles.appendChild(eltStyleId);
		}

		ext.getAny().add(eltRenderInfo);

		map.setExtension(ext);
	}

	private String parseYedNodeType(String szType) {
		String szGlyphClass = "";

		// Simple chemical
		if (szType.contains(FileUtils.COM_YWORKS_SBGN_SIMPLE_CHEMICAL)) {
			szGlyphClass = FileUtils.SBGN_SIMPLE_CHEMICAL;
		}
		// Unspecified entity
		if (szType.contains(FileUtils.COM_YWORKS_SBGN_UNSPECIFIED_ENTITY)) {
			szGlyphClass = FileUtils.SBGN_UNSPECIFIED_ENTITY;
		}
		// Perturbing agent
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_PERTURBING_AGENT)) {
			szGlyphClass = FileUtils.SBGN_PERTURBING_AGENT;
		}
		// Phenotype
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_PHENOTYPE)) {
			szGlyphClass = FileUtils.SBGN_PHENOTYPE;
		}
		// nucleic acid feature
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_NUCLEIC_ACID_FEATURE)) {
			szGlyphClass = FileUtils.SBGN_NUCLEIC_ACID_FEATURE;
		}
		// submap
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_SUBMAP)) {
			szGlyphClass = FileUtils.SBGN_SUBMAP;
		}
		// Macromolecule
		else if ((szType.contains(FileUtils.COM_YWORKS_SBGN_MACROMOLECULE))
				|| (szType.contains(FileUtils.COM_YWORKS_FLOWCHART_PROCESS))) {
			szGlyphClass = FileUtils.SBGN_BIOLOGICAL_ACTIVITY;
		}
		// Complex
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_COMPLEX)) {
			szGlyphClass = FileUtils.SBGN_COMPLEX;
		}
		// Tag
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_TAG)) {
			szGlyphClass = FileUtils.SBGN_TAG;
		}
		// State variable
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_STATE_VARIABLE)) {
			szGlyphClass = FileUtils.SBGN_STATE_VARIABLE;
		}
		// State variable
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_UNIT_OF_INFORMATION)) {
			szGlyphClass = FileUtils.SBGN_UNIT_OF_INFORMATION;
		}
		// Operator
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_OPERATOR)) {
			szGlyphClass = "operator";
		}
		// source and sink
		else if (szType.contains(FileUtils.COM_YWORKS_SBGN_SOURCE_AND_SINK)) {
			szGlyphClass = FileUtils.SBGN_SOURCE_AND_SINK;
		}

		return szGlyphClass;
	}

	private String processNodeList(NodeList nodeList) {
		String szContent = "";
		for (int temp = 0; temp < nodeList.getLength(); temp++) {
			Node nNode = nodeList.item(temp);
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				szContent = szContent.concat(getElementAttributes(eElement));
			}
		}
		return szContent;
	}

	private String getElementAttributes(Element eElement) {
		String szAttributeValues = "";
		for (int i = 0; i < eElement.getAttributes().getLength(); i++) {
			szAttributeValues = szAttributeValues.concat(eElement.getAttributes().item(i) + "\t");
		}
		return szAttributeValues;
	}

	private void createSBGNv02File(String inSbgnFile, String outSBGNv02File) {
		// Now read from "f" and put the result in "sbgn"
		Sbgn sbgn;
		try {
			sbgn = FileUtils.readFromFile(inSbgnFile);

			// map is a container for the glyphs and arcs
			Map map = (org.sbgn.bindings.Map) sbgn.getMap();

			Map map1 = new org.sbgn.bindings.Map();
			map1.setLanguage(map.getLanguage());

			// we can get a list of glyphs (nodes) in this map with getGlyph()
			for (Glyph g : map.getGlyph()) {
				String newGlyphId = "glyph_" + g.getId();
				newGlyphId = newGlyphId.replaceAll("::", "_");
				g.setId(newGlyphId);
				for (Glyph _glyph : g.getGlyph()) {
					String newVal = g.getId() + _glyph.getId();
					newVal = newVal.replaceAll("::", "_");
					_glyph.setId(newVal);
				}
				map1.getGlyph().add(g);
			}

			for (Arc a : map.getArc()) {
				String arcId = "arc_" + a.getId();
				arcId = arcId.replaceAll("::", "_");
				a.setId(arcId);
				map1.getArc().add(a);
			}

			// write everything to disk
			File outputFile = new File(outSBGNv02File);
			Sbgn sbgn1 = new Sbgn();
			sbgn1.setMap(map1);
			SbgnUtil.writeToFile(sbgn1, outputFile);
			System.out.println(
					"SBGN file validation: " + (SbgnUtil.isValid(outputFile) ? "validates" : "does not validate"));
		} catch (JAXBException | SAXException | IOException e2) {
			e2.printStackTrace();
		}

		System.out.println("Finished to generate the SBGN v02 file.");
	}
}
