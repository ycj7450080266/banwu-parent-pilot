const repository = require('../../services/taskRepository')

Page({
  data: { task: null, profile: { childName: '孩子' } },
  onLoad(options) {
    this.taskId = decodeURIComponent(options.id || '')
    this.refresh()
  },
  refresh() {
    const task = repository.getAll().find(item => item.id === this.taskId)
    this.setData({ task, profile: getApp().globalData.profile || wx.getStorageSync('banwu.profile') })
  },
  toggleTask() {
    if (!this.data.task) return
    repository.update(this.taskId, { done: !this.data.task.done, completedAt: this.data.task.done ? null : Date.now() })
    this.refresh()
    wx.showToast({ title: this.data.task.done ? '已恢复待办' : '已完成', icon: 'success' })
  },
  removeTask() {
    wx.showModal({ title: '移除这项待办？', content: '只会从本机预览清单中移除。', success: result => {
      if (result.confirm) { repository.remove(this.taskId); wx.navigateBack() }
    } })
  }
})

