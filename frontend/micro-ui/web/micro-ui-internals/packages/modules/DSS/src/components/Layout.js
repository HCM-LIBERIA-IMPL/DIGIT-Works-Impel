import React, { useCallback, useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import CustomAreaChart from "./CustomAreaChart";
import CustomBarChart from "./CustomBarChart";
import CustomHorizontalBarChart from "./CustomHorizontalBarChart";
import CustomPieChart from "./CustomPieChart";
import CustomTable from "./CustomTable";
import FilterContext from "./FilterContext";
import GenericChart from "./GenericChart";
import MetricChart from "./MetricChart";
import Summary from "./Summary";
import RichSummary from "./RichSummary";
import HeatMapChart from "./AdvancedMapCharts/HeatMap";
import BannerCard from "./BannerCard";
import LatLongMapChart from "./AdvancedMapCharts/LatLongMap"

let index = 1;

const showCustomLabel = (title, t) => {
  switch (title) {
    case "DSS_FSM_MONTHLY_WASTE_CAL":
      return `${t("DSS_WASTE_RECIEVED")} ${t(`DSS_WASTE_UNIT`)}`;
    default:
      return "";
  }
};

const Layout = ({ rowData, forHome = false }) => {
  const { t } = useTranslation();
  const { value } = useContext(FilterContext);
  const [searchQuery, onSearch] = useState("");
  const [chip, updateChip] = useState({});

  const renderChart = (chart, title) => {
    switch (chart.chartType) {
      case "table":
        return <CustomTable data={chart} onSearch={searchQuery} chip={chip} title={title} />;
      case "donut":
        return <CustomPieChart data={chart} title={title} />;
      case "line":
        return <CustomAreaChart data={chart} title={title} />;
      case "horizontalBar":
        return (
          <CustomHorizontalBarChart
            data={chart}
            xAxisType="number"
            yAxisType="category"
            layout="vertical"
            yDataKey="name"
            xDataKey=""
            showDrillDown={false}
            title={title}
          />
        );
      case "heatmap":
        return <HeatMapChart visualizer={chart} chartId={chart?.id} isNational={false} showLabel={true} />;
      case "bar":
        return <CustomHorizontalBarChart data={chart} title={title} yAxisLabel={showCustomLabel(title, t)} />;
      default:
        return null;
    }
  };

  const renderVisualizer = (visualizer, key, chip, onChipChange) => {
    const subHeader = t(`SUB_${visualizer.name}`);

    switch (visualizer.vizType) {
      case "metric-collection":
        return (
          <GenericChart header={visualizer.name} className="metricsTable" key={key} value={value}>
            <MetricChart data={visualizer} />
          </GenericChart>
        );
      case "chart":
        if (
          value?.filters?.tenantId?.length === 0 &&
          (visualizer?.charts?.[0].id === "fsmTopDsoByPerformance" || visualizer?.charts?.[0].id === "fsmBottomDsoByPerformance")
        )
          return null;
        return (
          <GenericChart
            key={key}
            value={value}
            header={
              visualizer?.charts?.[chip ? chip.filter((ele) => ele.active)?.[0]?.index : 0].chartType === "line"
                ? `${visualizer.name}`
                : visualizer.name
            }
            subHeader={subHeader !== `SUB_${visualizer.name}` ? subHeader : ""}
            chip={chip}
            updateChip={onChipChange}
            showDownload={visualizer?.charts?.[0].chartType === "table"}
            showSearch={visualizer?.charts?.[0].chartType === "table"}
            className={visualizer?.charts?.[0].chartType === "table" && "fullWidth"}
            onChange={(e) => onSearch(e.target.value)}
          >
            {/* {visualizer.charts.map((chart, key) => renderChart(chart, key))} */}
            {renderChart(visualizer?.charts?.[chip ? chip.filter((ele) => ele.active)?.[0]?.index : 0], visualizer.name)}
          </GenericChart>
        );
      case "performing-metric":
        if (
          value?.filters?.tenantId?.length > 0 &&
          (visualizer?.charts?.[0].id === "fsmTopUlbByPerformance" || visualizer?.charts?.[0].id === "fsmBottomUlbByPerformance")
        )
          return null;
        return (
          <GenericChart
            value={value}
            header={visualizer.name}
            subHeader={`(${t(`SUB_${visualizer.name}`)})`}
            key={key}
            chip={chip}
            updateChip={onChipChange}
          >
            <CustomBarChart
              data={visualizer?.charts?.[chip ? chip.filter((ele) => ele.active)?.[0]?.index : 0]}
              fillColor={index++ % 2 ? "RED" : "GREEN"}
              title={visualizer.name}
              showDrillDown={true}
            />
          </GenericChart>
        );
      case "collection":
      case "module":
        return <Summary header={visualizer.name} className="metricsTable" key={key} value={value} data={visualizer} />;
      case "stacked-collection":
        return <RichSummary header={visualizer.name} className="metricsTable" key={key} value={value} data={visualizer} />;
      case "bannercard":
        return <BannerCard data={visualizer.charts[0]} chartId={visualizer?.id} chartName={visualizer?.name} />;
      case "latlong":
        return <LatLongMapChart data={visualizer} chartId={visualizer?.id} chartName={visualizer?.name} />;
      default:
        return null;
    }
  };
  useEffect(() => {
    let chipData = {};
    rowData.vizArray.map((chart) => {
      if (chart?.charts?.length > 1) {
        chipData[chart.name] = chart.charts.map((ele, ind) => ({ tabName: ele.tabName, active: ind === 0, index: ind }));
      }
    });
    updateChip({ ...chipData });
  }, [rowData.vizArray]);
  return (
    <div className="chart-row">
      {rowData.vizArray.map(
        useCallback(
          (chart, key) => {
            let chipData = chip?.[chart.name];
            let onChipChange = (index) =>
              updateChip((oldState) => {
                let prevChip = oldState[chart.name];
                oldState[chart.name] = prevChip.map((ele) => ({ ...ele, active: ele.index === index }));
                return { ...oldState };
              });
            return renderVisualizer(chart, key, chipData, onChipChange);
          },
          [renderVisualizer, chip]
        )
      )}
    </div>
  );
};

export default Layout;
