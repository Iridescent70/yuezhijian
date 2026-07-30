import request from '@/config/axios'

export interface WorkbenchShortcut {
  code: string
  name: string
  route: string
  permission: string
}

export interface WorkbenchOverview {
  businessDate: string
  appointmentCount: number
  customerTraffic: number
  revenue: number
  pendingTaskCount: number
  shortcuts: WorkbenchShortcut[]
}

export const getWorkbenchOverview = () =>
  request.get<WorkbenchOverview>({ url: '/workbench/overview' })
