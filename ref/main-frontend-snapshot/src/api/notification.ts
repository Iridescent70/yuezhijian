import { apiRequest } from './http'
import type {
  Announcement,
  AnnouncementPayload,
  AnnouncementStatus,
  NotificationItem,
  NotificationMessageType,
  NotificationTemplate,
  NotificationTemplatePayload,
  PageResult,
} from '@/types/api'

export function getNotifications(params: {
  messageType?: NotificationMessageType
  readStatus?: 'READ' | 'UNREAD'
  publishedFrom?: string
  publishedTo?: string
  page?: number
  size?: number
}): Promise<PageResult<NotificationItem>> {
  return apiRequest({ method: 'GET', url: '/notifications', params })
}

export function getNotification(id: number): Promise<NotificationItem> {
  return apiRequest({ method: 'GET', url: `/notifications/${id}` })
}

export function getUnreadNotificationCount(): Promise<number> {
  return apiRequest<{ count: number }>({ method: 'GET', url: '/notifications/unread-count' })
    .then(result => result.count)
}

export function markNotificationRead(id: number): Promise<NotificationItem> {
  return apiRequest({ method: 'POST', url: `/notifications/${id}/read` })
}

export function markAllNotificationsRead(messageType?: NotificationMessageType): Promise<number> {
  return apiRequest<{ count: number }>({
    method: 'POST', url: '/notifications/read-all', params: { messageType },
  }).then(result => result.count)
}

export function getAnnouncements(params: {
  storeId?: number
  keyword?: string
  status?: AnnouncementStatus
  page?: number
  size?: number
}): Promise<PageResult<Announcement>> {
  return apiRequest({ method: 'GET', url: '/announcements', params })
}

export function getAnnouncement(id: number): Promise<Announcement> {
  return apiRequest({ method: 'GET', url: `/announcements/${id}` })
}

export function createAnnouncement(payload: AnnouncementPayload): Promise<Announcement> {
  return apiRequest({ method: 'POST', url: '/announcements', data: payload })
}

export function updateAnnouncement(
  id: number, payload: AnnouncementPayload & { version: string },
): Promise<Announcement> {
  return apiRequest({ method: 'PUT', url: `/announcements/${id}`, data: payload })
}

export function getNotificationTemplates(params: {
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
}): Promise<NotificationTemplate[]> {
  return apiRequest({ method: 'GET', url: '/notification-templates', params })
}

export function getNotificationTemplate(id: number): Promise<NotificationTemplate> {
  return apiRequest({ method: 'GET', url: `/notification-templates/${id}` })
}

export function createNotificationTemplate(
  payload: NotificationTemplatePayload,
): Promise<NotificationTemplate> {
  return apiRequest({ method: 'POST', url: '/notification-templates', data: payload })
}

export function updateNotificationTemplate(
  id: number, payload: Omit<NotificationTemplatePayload, 'eventCode'> & { version: string },
): Promise<NotificationTemplate> {
  return apiRequest({ method: 'PUT', url: `/notification-templates/${id}`, data: payload })
}

export function sendTestNotification(
  templateId: number, variables: Record<string, string>,
): Promise<{ notificationNo: string; title: string; body: string }> {
  return apiRequest({ method: 'POST', url: '/notifications/test', data: { templateId, variables } })
}
