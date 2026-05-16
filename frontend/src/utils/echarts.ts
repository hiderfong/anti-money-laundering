import type { ECharts } from 'echarts'

let echartsModule: typeof import('echarts') | null = null

export async function getEcharts() {
  if (!echartsModule) {
    echartsModule = await import('echarts')
  }
  return echartsModule
}

export function disposeEchart(chart: ECharts | null | undefined) {
  chart?.dispose()
}

