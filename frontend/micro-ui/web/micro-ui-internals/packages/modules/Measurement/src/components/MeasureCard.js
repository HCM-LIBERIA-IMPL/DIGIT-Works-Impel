import { Button, Card, Toast, Amount } from "@egovernments/digit-ui-react-components";
import React, { useEffect, useReducer, useState, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { useHistory } from "react-router-dom/cjs/react-router-dom.min";
import MeasureRow from "./MeasureRow";
import cloneDeep from 'lodash/cloneDeep';



{/* <Amount customStyle={{ textAlign: 'right'}} value={Math.round(value)} t={t}></Amount> */ }
const MeasureCard = React.memo(({ columns, consumedQty, setConsumedQty, setShowMeasureCard, initialState = {}, setInitialState, register, setValue, tableData, tableKey, tableIndex, unitRate, isView, isEstimates }) => {
  const tenantId = Digit.ULBService.getCurrentTenantId();

  const { t } = useTranslation();
  const history = useHistory();
  const [total, setTotal] = useState(consumedQty);
  const isEstimate = isEstimates;

  const validate = (value) => {
    if (value === null || value === undefined || value === "" || value === "0") {
      return 1;
    } else {
      return value;
    }
  }

  const reducer = (state, action) => {
    switch (action.type) {
      case "ADD_ROW":
        const { state: newRow } = action;
        return { ...state, tableState: [...state.tableState, newRow] };
      case "UPDATE_ROW":
        const {
          state: { id, value, row, type },
        } = action;
        const { tableState } = state;
        let findIndex = tableState.findIndex((row, index) => {
          return index + 1 === id;
        });
        switch (type) {
          case "number":
            tableState[findIndex].number = value;
            break;
          case "length":
            tableState[findIndex].length = value;
            break;
          case "width":
            tableState[findIndex].width = value;
            break;
          case "height":
            tableState[findIndex].height = value;
            break;
          case "isDeduction":
            tableState[findIndex].isDeduction = value;
            break;
          case "description":
            tableState[findIndex].description = value;
            break;
          default:

        }
        const element = tableState[findIndex];
        const calculatedValue =
          (validate(element.number)) *
          (validate(element.length)) *
          (validate(element.width)) *
          (validate(element.height));

        tableState[findIndex].noOfunit = calculatedValue;
        tableState[findIndex].rowAmount = unitRate * calculatedValue;
        setTotal(tableState.reduce((acc, curr) => acc + validate(curr.noOfunit), 0));

        return { ...state, tableState };
      case "REMOVE_ROW":
        const { id: rowIdToRemove } = action;
        const updatedTableState = state.tableState.filter((row, index) => index + 1 !== rowIdToRemove);
        setTotal(updatedTableState.reduce((acc, curr) => acc + validate(curr.noOfunit), 0));
        return { ...state, tableState: updatedTableState };
      case "CLEAR_STATE":
        const clearedTableState = state.tableState.map((item) => ({
          ...item,
          height: "0",
          width: "0",
          length: "0",
          number: "0",
          noOfunit: 0,
          rowAmount: 0,
        }));
        setTotal(clearedTableState.reduce((acc, curr) => acc + validate(curr.noOfunit), 0));
        return { ...state, tableState: clearedTableState };

      default:
        return state;
    }
  };

  const [state, dispatch] = useReducer(reducer, initialState);
  useEffect(() => {
    register("table", tableData);
  }, [])

  const getStyles = (index) => {
    let obj = {};
    switch (index) {
      case 1:
        obj = { width: "1rem" };
        break;
      case 2:
        obj = { width: "30%" };
        break;
      case 3:
        obj = { width: "27rem" };
        break;
      case 4:
        obj = { width: "27rem" };
        break;
      case 5:
        obj = { width: "3%" };
        break;
      default:
        obj = { width: "92rem" };
        break;
    }
    return obj;
  };



  const renderHeader = () => {
    return columns?.map((key, index) => {
      return (
        <th key={index} style={getStyles(index + 1)}>
          {" "}
          {key}{" "}
        </th>
      );
    });
  };

  const renderBody = () => {
    return state?.tableState?.map((value, index) => {
      return <MeasureRow value={value} index={index} key={index} state={state} dispatch={dispatch} isView={isView} isEstimate={isEstimate} />;
    });
  };

  return (
    <Fragment>


      <table className="table reports-table sub-work-table" >
        <thead>
          <tr>{renderHeader()}</tr>
        </thead>
        <tbody>
          {renderBody()}
          <tr>
            <td colSpan={"4"}>
              <div style={{ display: "flex", flexDirection: "row" }}>
                {isView ? (
                  <Button
                    className={"outline-btn"}
                    label={t("MB_CLOSE")}
                    onButtonClick={() => {
                      setShowMeasureCard(false);
                    }}
                  />
                ) : (<>
                  {isEstimate && <Button className={"outline-btn"} label={t("MB_ADD_ROW")} onButtonClick={() => {
                    dispatch({
                      type: "ADD_ROW",
                      state: {
                        sNo: state.tableState.length + 1,
                        targetId: "",
                        isDeduction: "",
                        description: "",
                        id: null,
                        height: 0,
                        width: 0,
                        length: 0,
                        number: 0,
                        noOfunit: 0,
                        rowAmount: 0,
                        consumedRowQuantity: 0,
                      },
                    })
                  }} />}
                  <Button className={"outline-btn"} label={t("MB_CLEAR")} onButtonClick={() => {
                    dispatch({ type: "CLEAR_STATE" });
                  }} />
                  <Button className={"outline-btn"} label={t("MB_DONE")} onButtonClick={() => {
                    tableData[tableKey][tableIndex].measures = state.tableState;
                    tableData[tableKey][tableIndex].amount = parseFloat(tableData[tableKey][tableIndex].measures.reduce((total, item) => total + item.rowAmount, 0)).toFixed(2);
                    setValue("table", tableData);
                    setInitialState(state);
                    setConsumedQty(total);
                    setShowMeasureCard(false);
                  }} />

                </>)}
              </div>
            </td>
            <td colSpan={"4"}>
              {t("MB_SUBTOTAL")}: {total}
            </td>
          </tr>
        </tbody>
      </table>
    </Fragment>
  );
});

export default MeasureCard;