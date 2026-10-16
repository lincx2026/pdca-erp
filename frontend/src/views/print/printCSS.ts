export function printCSS(): string {
  return `
  @page { size: A4; margin: 10mm; }
  * { margin:0; padding:0; box-sizing:border-box; font-family: 'Microsoft YaHei', 'SimSun', sans-serif; }
  body { color:#222; font-size:12px; line-height:1.7; }

  /* 打印按钮不打印 */
  .no-print { display:none !important; }

  /* A4 页面容器 */
  .a4-page { width:100%; position:relative; min-height:270mm; }

  /* 公司抬头区域 */
  .doc-header {
    display:flex; justify-content:space-between; align-items:flex-start;
    margin-bottom:8px; padding-bottom:8px;
    border-bottom: 3px double #1a1a1a;
  }
  .doc-header-left { width:30%; }
  .logo-box {
    width:60px; height:60px; border:2px solid #1a1a1a;
    display:flex; align-items:center; justify-content:center;
    font-size:10px; color:#999; text-align:center;
  }
  .doc-header-center { text-align:center; flex:1; padding:0 10px; }
  .company-name { font-size:20px; font-weight:bold; letter-spacing:1px; }
  .doc-title { font-size:16px; font-weight:bold; margin:4px 0; color:#1a1a1a; letter-spacing:2px; }
  .company-info { font-size:10px; color:#666; margin-top:2px; }
  .company-info span { margin:0 6px; }
  .doc-header-right { width:22%; text-align:right; }
  .doc-no-label { font-size:10px; color:#666; }
  .doc-no-value { font-size:14px; font-weight:bold; font-family:'Courier New',monospace; }
  .doc-date { font-size:10px; color:#666; margin-top:2px; }

  /* 状态水印 */
  .watermark {
    position:absolute; top:40%; left:50%; transform:translate(-50%,-50%) rotate(-30deg);
    font-size:80px; font-weight:bold; color:rgba(200,200,200,0.25);
    z-index:0; pointer-events:none; letter-spacing:8px;
  }
  .a4-content { position:relative; z-index:1; }

  /* 段落标题 */
  .section-title {
    font-size:13px; font-weight:bold; margin:10px 0 4px;
    padding-left:6px; border-left:4px solid #1a1a1a;
    line-height:1.4;
  }

  /* 信息表（工单/订单基本信息） */
  .info-table { width:100%; border-collapse:collapse; margin-bottom:6px; }
  .info-table td {
    border:1px solid #bbb; padding:5px 8px; font-size:11px;
    height:26px;
  }
  .info-table .label {
    background:#eef1f5; font-weight:bold; width:12%;
    text-align:right; color:#333; white-space:nowrap;
  }

  /* 明细/BOM/工序表格 */
  .data-table { width:100%; border-collapse:collapse; margin-bottom:6px; }
  .data-table th {
    background:#2c3e50; color:#fff; font-weight:bold;
    border:1px solid #2c3e50; padding:5px 6px; font-size:11px;
    text-align:center; white-space:nowrap;
  }
  .data-table td {
    border:1px solid #bbb; padding:4px 6px; font-size:11px;
    height:24px;
  }
  .data-table tbody:nth-child(even) tr:nth-child(even) { background:#f8f9fa; }
  .data-table tr:nth-child(even) { background:#f8f9fa; }

  /* 签字栏 */
  .sign-table { width:100%; border-collapse:collapse; margin-top:8px; }
  .sign-table td {
    border:1px solid #bbb; padding:5px; font-size:11px;
    text-align:center; height:40px;
  }
  .sign-table .sign-label {
    width:12%; font-weight:bold; background:#eef1f5;
  }
  .sign-table .sign-blank { width:21%; }
  .stamp-area {
    width:50px; height:50px; border:2px dashed #aaa; border-radius:50%;
    display:inline-block; vertical-align:middle; margin-left:4px;
    font-size:9px; color:#bbb; line-height:46px; text-align:center;
  }

  /* 页脚 */
  .footer-note {
    margin-top:10px; padding-top:6px; border-top:1px solid #ddd;
    font-size:10px; color:#888; display:flex; justify-content:space-between;
  }

  /* 空白填写行 */
  .fill-row td { height:28px; }

  /* 小标题（交货条款等） */
  .sub-section {
    font-size:11px; font-weight:bold; margin:8px 0 4px;
    background:#eef1f5; padding:3px 8px; border-radius:2px;
  }
  `
}
