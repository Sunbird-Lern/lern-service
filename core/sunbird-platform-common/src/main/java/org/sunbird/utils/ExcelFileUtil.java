package org.sunbird.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sunbird.logging.LoggerUtil;

/**
 * Utility class to generate Excel files using Apache POI.
 * Extends basic FileUtil functionalities.
 */
public class ExcelFileUtil extends FileUtil {

  private static final LoggerUtil logger = new LoggerUtil(ExcelFileUtil.class);

  /**
   * Writes the provided data values to an Excel file (.xlsx).
   * ML-04: Uses try-with-resources for proper resource management of XSSFWorkbook and FileOutputStream.
   *
   * @param fileName The name of the file to be created (without extension).
   * @param dataValues A list of rows, where each row is a list of cell objects (Strings, Integers, etc.).
   * @return The created File object, or null (and fails gracefully) if an error occurs.
   */
  public File writeToFile(String fileName, List<List<Object>> dataValues) {
    File file = null;
    int rownum = 0;
    
    logger.info("ExcelFileUtil:writeToFile: Starting file creation for: " + fileName);

    try (XSSFWorkbook workbook = new XSSFWorkbook();
         FileOutputStream out = new FileOutputStream(new File(fileName + ".xlsx"))) {
      // Create a blank sheet
      XSSFSheet sheet = workbook.createSheet("Data");
      file = new File(fileName + ".xlsx");

      for (Object key : dataValues) {
        Row row = sheet.createRow(rownum);
        List<Object> objArr = dataValues.get(rownum);
        int cellnum = 0;
        for (Object obj : objArr) {
          Cell cell = row.createCell(cellnum++);
          if (obj instanceof String) {
            cell.setCellValue((String) obj);
          } else if (obj instanceof Integer) {
            cell.setCellValue((Integer) obj);
          } else if (obj instanceof List) {
            cell.setCellValue(getListValue(obj));
          } else if (obj instanceof Double) {
            cell.setCellValue((Double) obj);
          } else {
            if (null != (obj)) {
              cell.setCellValue(obj.toString());
            }
          }
        }
        rownum++;
      }

      workbook.write(out);
      logger.info(
          "ExcelFileUtil:writeToFile: File created successfully. Name: "
              + fileName
              + ", Rows: "
              + rownum);
    } catch (Exception e) {
      logger.error(
          "ExcelFileUtil:writeToFile: Error occurred while creating file: " + fileName, e);
    }
    return file;
  }
}
