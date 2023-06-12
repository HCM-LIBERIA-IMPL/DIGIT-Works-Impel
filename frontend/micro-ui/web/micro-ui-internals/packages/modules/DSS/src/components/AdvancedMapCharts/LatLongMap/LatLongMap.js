import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ReactTooltip from "react-tooltip";
import { ZoomableGroup, Geographies, ComposableMap, Geography, Marker } from "react-simple-maps";
import { Icon } from "../../common/Icon";

const LatLongMap = ({ chartId, mapData, chartData, markers, filterFeature, tableData = {} }) => {
  const { t } = useTranslation();
  const [geoJSONData, setGeoJSONData] = useState(null);
  const [mapStyle, setMapStyle] = useState({});
  const [pointName, setPointName] = useState("");
  const [zoom, setZoom] = useState({
    current: 0,
    min: 0,
    max: 0,
  });
  const isMobile = window.Digit.Utils.browser.isMobile();

  const [toolTipContent, setTooltipContent] = useState("");

  useEffect(() => {
    if (mapData === {}) return;
    const { center, zoomLevels, geoJSON, fontSize } = mapData;

    if (isMobile) {
      setMapStyle({ fontSize: fontSize?.mobile });

      setZoom({
        current: zoomLevels?.default,
        min: zoomLevels?.minZoom - 10,
        max: zoomLevels?.maxZoom + 20,
        coordinates: center,
      });
    } else {
      setMapStyle({ fontSize: fontSize?.desktop });
    }

    setZoom({
      current: zoomLevels?.default,
      min: zoomLevels?.minZoom,
      max: zoomLevels?.maxZoom + 100,
      coordinates: center,
    });

    setGeoJSONData(geoJSON);
  }, [mapData, chartData, isMobile]);

  const ZoomButtons = () => {
    const button = (label) => {
      const handleZoomIn = () => {
        const increment = zoom.current + 4;
        if (increment > zoom.max) {
          setZoom((prev) => {
            return { ...prev, current: zoom.max };
          });
          return;
        }

        setZoom((prev) => {
          return { ...prev, current: increment };
        });
      };

      const handleZoomOut = () => {
        const decrement = zoom.current - 4;
        if (decrement < zoom.min) {
          setZoom((prev) => {
            return { ...prev, current: zoom.min };
          });
          return;
        }

        setZoom((prev) => {
          return { ...prev, current: decrement };
        });
      };

      return (
        <button
          style={{
            display: "flex",
            flexDirection: "row",
            justifyContent: "center",
            alignItems: "center",
            padding: "5px 10px",
            width: "100%",
            background: "#FFFFFF",
            border: "1px solid #D6D5D4",
            boxShadow: "0px 1px 2px rgba(0, 0, 0, 0.16)",
            color: "#F47738",
            fontWeight: "700",
            fontSize: "16px",
            cursor: "pointer",
          }}
          onClick={() => {
            if (label === "+") {
              handleZoomIn();
            } else {
              handleZoomOut();
            }
          }}
        >
          {label}
        </button>
      );
    };

    return (
      <div
        style={{
          display: "flex",
          justifyContent: "end",
          flexDirection: "column",
          alignItems: "center",
          marginBottom: "24px",
          marginRight: "24px"
        }}
      >
        {button("+")}
        {button("-")}
      </div>
    );
  };

  const toTitleCase = (str) => {
    if (str) {
      return str.charAt(0).toUpperCase() + str.slice(1);
    }
  };

  const tooltip = (dataTip) => {
    return (
      dataTip.name &&  <div
        style={{
          display: "flex",
          padding: "5px",
          justifyContent: "center",
          alignItems: "center",
        }}
      >
        {<div style={{ fontWeight: 700, fontSize: "16px", margin: "10px" }}>{toTitleCase(dataTip.name)}</div>}
        {chartData.hasOwnProperty(dataTip.name) && dataTip.value ? (
          <React.Fragment>
            <div style={{ margin: "10px", fontWeight: 400, fontSize: "14px" }}>{dataTip.value}</div>
            <div style={{ margin: "10px", fontWeight: 400, fontSize: "14px" }}>{t(`DSS_HEALTH_${Digit.Utils.locale.getTransformedLocale(dataTip.string)}`)}</div>
          </React.Fragment>
        ) : null}
      </div>
    );
  };

  const handleGeographyClick = (name) => {
    if (tableData.hasOwnProperty(name)) setPointName(name);
  };
  const Recentre = () => {
    const recentreHandler = () => {
      setZoom((prev) => {
        return { ...prev, coordinates: mapData.center };
      });
    }
    return (
    <div style={{    
      display: "flex",
      marginBottom: "24px",
      marginLeft: "24px",
      cursor: "pointer"
    }} >
        <div style={{border: "1px solid #F47738", display: "flex",flexDirection: "row"}} onClick={() => {recentreHandler();}}>
          <div style={{margin:"9px"}}>{Icon("recenter-map")}</div>
          <div style={{color: "#F47738", fontSize: "12px", fontWeight: 700, margin: "9px"}}>{t("DSS_MAP_RECENTRE")}</div>
        </div>
    </div>)
  }
  const PointsDataTable = () => {
    return (
      <div
        style={{
          display: "flex",
          marginTop: "24px",
          marginLeft: "24px",
          border: "1px solid #D6D5D4",
          borderRadius: "4px",
          maxHeight: "480px",
          minWidth: "40%",
          backgroundColor: "#FAFAFA",
          padding: "40px",
        }}
      >
        <table style={{ width: "100%" }}>
          <tbody>
            {Object.keys(tableData[pointName]).map((key) => (
              <tr style={{ borderBottom: "1px solid #D6D5D4", textAlign: "left" }}>
                <td style={{ color: "#505A5F", width: "60%" }}>
                  <strong>{t(`DSS_TB_${Digit.Utils.locale.getTransformedLocale(key)}`)}</strong>
                </td>
                <td style={{ colour: "#0B0C0C" }}>
                  {key.toLowerCase() === "status" ? (
                    <text
                      style={{
                        color: tableData[pointName][key] > 0 ? "#00703C" : "#D4351C",
                        backgroundColor: tableData[pointName][key] > 0 ? "#D9EBE3" : "#d4351c1f",
                        borderRadius: "10px",
                        padding: "2px 10px",
                      }}
                    >
                      {tableData[pointName][key] > 0 ? "Sufficient" : "Insufficient"}
                    </text>
                  ) : (
                    (typeof tableData[pointName][key]==="number" ? Math.floor(tableData[pointName][key]): tableData[pointName][key])
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <div style={{ display: "flex" }}>
      <div
        key={chartId}
        style={{
          display: "flex",
          justifyContent: "center",
          marginTop: "24px",
          border: "1px solid #D6D5D4",
          borderRadius: "4px",
          height: "480px",
          flex: 1,
        }}
      >
        <div style={{ width: "90%" , display: "flex", flexDirection:"column"}}>
          {geoJSONData ? (
            <React.Fragment>
                <ReactTooltip id={`tooltip-for-${chartId}`} border={true} type={"light"}>
                  {tooltip(toolTipContent)}
                </ReactTooltip>
              <ComposableMap
                projection="geoMercator"
                data-tip=""
                data-for={`tooltip-for-${chartId}`}
                projectionConfig={{
                  scale: isMobile ? 300 : 100,
                }}
                width={800}
                height={400}
                style={{ width: "100%", height: "100%" }}
              >
                <ZoomableGroup
                  center={zoom?.coordinates}
                  zoom={zoom?.current}
                  minZoom={zoom?.min}
                  maxZoom={zoom?.max}
                  onMoveEnd={(d) => {
                    setZoom((prev) => {
                      return {
                        ...prev,
                        current: d.zoom,
                        coordinates: d.coordinates,
                      };
                    });
                  }}
                >
                  <Geographies geography={geoJSONData}>
                    {({ geographies }) => {
                      return geographies.map((geo) => {
                        const locationName = geo.properties?.name?.toLowerCase();
                        const value = chartData[locationName];
                        if (filterFeature && filterFeature !== locationName) {
                          return null;
                        }
                        let cursor = "pointer",
                          stroke = "#C6C6C6";

                        return (
                          <Geography
                            key={geo.rsmKey}
                            geography={geo}
                            strokeWidth={0.03}
                            fill="#F0E8E8"
                            stroke="#C6C6C6"
                            onClick={() => handleGeographyClick(locationName)}
                            style={{
                              default: { outline: "none", fill: "#F6F6F6" },
                              hover: { outline: "none", stroke, cursor, fill: "#F477381F" },
                              pressed: { outline: "none" },
                            }}
                            onMouseEnter={() => {
                              markers.boundaryNames.includes(locationName) && setTooltipContent({ name:locationName, value, string: markers?.tooltipString })
                            }}
                            onMouseLeave={() => {
                              setTooltipContent('')
                            }}
                          />
                        );
                      });
                    }}
                  </Geographies>
                  {markers.markersArray.map(({ name, origin }) => {
                    const value = chartData[name];
                    if (markers.showPoints.includes(name)) {
                      return (
                        <Marker
                          key={name}
                          cursor={tableData !== {} ? "pointer" : "default"}
                          coordinates={[origin.lng, origin.lat]}
                          onClick={() => {
                            Object.keys(tableData).length > 0 && setPointName(name);
                          }}
                          onMouseEnter={() => {
                            markers.boundaryNames.includes(name) && setTooltipContent({ name, value, string: markers?.tooltipString })
                          }}
                          onMouseLeave={() => {
                            setTooltipContent('')
                          }}
                        >
                          {markers.boundaryNames.includes(name) ? (
                            <text
                              textAnchor="middle"
                              style={{ fill: "#505A5F", fontSize: mapStyle?.fontSize || "0.2px" }}
                            >
                              {chartData.name && toTitleCase(name)}
                            </text>
                          ) : (
                            <circle fill="#0BABDE" r="0.05" stroke="#C6C6C6" strokeWidth={0.01} />
                          )}
                        </Marker>
                      );
                    }
                  })}
                </ZoomableGroup>
              </ComposableMap>
            </React.Fragment>
          ) : null}
        <Recentre />
        </div>
        <ZoomButtons />
      </div>
      {pointName !== "" && Object.keys(tableData).length !== 0 ? <PointsDataTable /> : null}
    </div>
  );
};
export default LatLongMap;
