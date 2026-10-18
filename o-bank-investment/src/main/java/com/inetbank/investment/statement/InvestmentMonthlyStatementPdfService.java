package com.inetbank.investment.statement;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class InvestmentMonthlyStatementPdfService {

    private static final Color BLUE =
            new Color(25, 64, 120);

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm");

    public byte[] create(Map<String, Object> statement) {

        try (ByteArrayOutputStream output =
                new ByteArrayOutputStream()) {

            Document document = new Document(
                    PageSize.A4.rotate(),
                    24,
                    24,
                    28,
                    28
            );

            PdfWriter.getInstance(document, output);

            document.open();

            addHeader(document, statement);
            addSummary(document, statement);
            addOrders(document, statement);
            addHoldings(document, statement);
            addFooter(document);

            document.close();

            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Investment statement PDF could not be generated.",
                    exception
            );
        }
    }

    private void addHeader(
            Document document,
            Map<String, Object> statement)
            throws Exception {

        Paragraph title = new Paragraph(
                "O Bank - Investment Portfolio Statement",
                font(18, Font.BOLD, BLUE)
        );

        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);

        document.add(title);

        Paragraph period = new Paragraph(
                "Statement period: "
                        + value(statement.get("statementFrom"))
                        + " to "
                        + value(statement.get("statementTo")),
                font(10, Font.NORMAL, Color.DARK_GRAY)
        );

        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(16);

        document.add(period);
    }

    private void addSummary(
            Document document,
            Map<String, Object> statement)
            throws Exception {

        PdfPTable table = new PdfPTable(4);

        table.setWidthPercentage(100);
        table.setWidths(new float[]{
                1.3f, 1.3f, 1.3f, 1.3f
        });
        table.setSpacingAfter(16);

        addSummaryCell(
                table,
                "Successful purchases",
                money(statement.get("totalSuccessfulPurchases"))
        );

        addSummaryCell(
                table,
                "Completed redemptions",
                money(statement.get("totalCompletedRedemptions"))
        );

        addSummaryCell(
                table,
                "Net investment",
                money(statement.get("netInvestmentForMonth"))
        );

        addSummaryCell(
                table,
                "Current portfolio value",
                money(statement.get("currentPortfolioValue"))
        );

        document.add(table);
    }

    private void addOrders(
            Document document,
            Map<String, Object> statement)
            throws Exception {

        Paragraph heading = new Paragraph(
                "Investment Activity",
                font(13, Font.BOLD, BLUE)
        );

        heading.setSpacingAfter(8);

        document.add(heading);

        PdfPTable table = new PdfPTable(8);

        table.setWidthPercentage(100);
        table.setWidths(new float[]{
                1.25f, 1.5f, 0.9f, 1.0f,
                1.0f, 0.8f, 1.0f, 1.4f
        });

        addHeaderCell(table, "Date");
        addHeaderCell(table, "Product");
        addHeaderCell(table, "Kind");
        addHeaderCell(table, "Mode");
        addHeaderCell(table, "Amount");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Status");
        addHeaderCell(table, "Reference");

        List<Map<String, Object>> entries =
                rows(statement.get("entries"));

        if (entries.isEmpty()) {
            addEmptyRow(
                    table,
                    8,
                    "No investment activity was found "
                            + "for this statement period."
            );
        } else {
            for (Map<String, Object> entry : entries) {
                addBodyCell(
                        table,
                        dateTime(entry.get("requestedAt"))
                );
                addBodyCell(table, value(entry.get("productName")));
                addBodyCell(table, value(entry.get("orderKind")));
                addBodyCell(
                        table,
                        value(entry.get("investmentMode"))
                );
                addBodyCell(table, money(entry.get("amount")));
                addBodyCell(table, value(entry.get("quantity")));
                addBodyCell(table, value(entry.get("status")));
                addBodyCell(
                        table,
                        value(entry.get("referenceNumber"))
                );
            }
        }

        document.add(table);
    }

    private void addHoldings(
            Document document,
            Map<String, Object> statement)
            throws Exception {

        Paragraph heading = new Paragraph(
                "Active Holdings",
                font(13, Font.BOLD, BLUE)
        );

        heading.setSpacingBefore(18);
        heading.setSpacingAfter(8);

        document.add(heading);

        PdfPTable table = new PdfPTable(6);

        table.setWidthPercentage(100);
        table.setWidths(new float[]{
                1.8f, 1.0f, 1.0f,
                1.2f, 1.2f, 1.0f
        });

        addHeaderCell(table, "Product");
        addHeaderCell(table, "Type");
        addHeaderCell(table, "Quantity");
        addHeaderCell(table, "Invested");
        addHeaderCell(table, "Current value");
        addHeaderCell(table, "Gain / loss");

        List<Map<String, Object>> holdings =
                rows(statement.get("activeHoldings"));

        if (holdings.isEmpty()) {
            addEmptyRow(
                    table,
                    6,
                    "No active investment holdings are available."
            );
        } else {
            for (Map<String, Object> holding : holdings) {
                addBodyCell(
                        table,
                        value(holding.get("productName"))
                );
                addBodyCell(
                        table,
                        value(holding.get("productType"))
                );
                addBodyCell(
                        table,
                        value(holding.get("quantity"))
                );
                addBodyCell(
                        table,
                        money(holding.get("investedAmount"))
                );
                addBodyCell(
                        table,
                        money(holding.get("currentValue"))
                );
                addBodyCell(
                        table,
                        money(holding.get("gainLoss"))
                );
            }
        }

        document.add(table);
    }

    private void addFooter(Document document)
            throws Exception {

        Paragraph footer = new Paragraph(
                "\nThis is an automated O Bank investment statement. "
                        + "Please do not reply to this email.",
                font(9, Font.ITALIC, Color.DARK_GRAY)
        );

        footer.setAlignment(Element.ALIGN_CENTER);

        document.add(footer);
    }

    private void addSummaryCell(
            PdfPTable table,
            String label,
            String summaryValue) {

        PdfPCell cell = new PdfPCell();

        cell.setPadding(8);
        cell.setBackgroundColor(
                new Color(239, 246, 255)
        );

        cell.addElement(new Paragraph(
                label,
                font(8, Font.BOLD, BLUE)
        ));

        cell.addElement(new Paragraph(
                summaryValue,
                font(10, Font.NORMAL, Color.BLACK)
        ));

        table.addCell(cell);
    }

    private void addHeaderCell(
            PdfPTable table,
            String text) {

        PdfPCell cell = new PdfPCell(new Paragraph(
                text,
                font(8, Font.BOLD, Color.WHITE)
        ));

        cell.setBackgroundColor(BLUE);
        cell.setPadding(6);
        cell.setHorizontalAlignment(
                Element.ALIGN_CENTER
        );

        table.addCell(cell);
    }

    private void addBodyCell(
            PdfPTable table,
            String text) {

        PdfPCell cell = new PdfPCell(new Paragraph(
                value(text),
                font(8, Font.NORMAL, Color.BLACK)
        ));

        cell.setPadding(5);

        table.addCell(cell);
    }

    private void addEmptyRow(
            PdfPTable table,
            int columns,
            String text) {

        PdfPCell cell = new PdfPCell(new Paragraph(
                text,
                font(10, Font.NORMAL, Color.DARK_GRAY)
        ));

        cell.setColspan(columns);
        cell.setPadding(12);
        cell.setHorizontalAlignment(
                Element.ALIGN_CENTER
        );

        table.addCell(cell);
    }

    private List<Map<String, Object>> rows(Object value) {

        if (!(value instanceof List<?> values)) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();

        for (Object item : values) {
            if (!(item instanceof Map<?, ?> rawRow)) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();

            rawRow.forEach((key, itemValue) ->
                    row.put(String.valueOf(key), itemValue));

            rows.add(row);
        }

        return rows;
    }

    private String dateTime(Object value) {

        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime()
                    .format(DATE_TIME_FORMAT);
        }

        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }

        return value(value);
    }

    private String money(Object value) {

        if (value == null) {
            return "-";
        }

        BigDecimal amount = value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(String.valueOf(value));

        return "INR " + amount.toPlainString();
    }

    private Font font(
            float size,
            int style,
            Color color) {

        return FontFactory.getFont(
                FontFactory.HELVETICA,
                size,
                style,
                color
        );
    }

    private String value(Object value) {

        if (value == null) {
            return "-";
        }

        String text = String.valueOf(value);

        return text.isBlank() ? "-" : text;
    }
}