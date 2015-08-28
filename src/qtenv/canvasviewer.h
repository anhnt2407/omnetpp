//==========================================================================
//  CANVASVIEWER.H - part of
//
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2015 Andras Varga
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/


#ifndef CANVASVIEWER_H
#define CANVASVIEWER_H

#include <QGraphicsView>

class QGraphicsPixmapItem;

namespace omnetpp {

class cCanvas;
class cObject;

namespace qtenv {

class CanvasRenderer;
class FigureRenderingHints;
class GraphicsLayer;

class CanvasViewer : public QGraphicsView
{
    Q_OBJECT

private:
    cCanvas *object;
    CanvasRenderer *canvasRenderer;
    QRectF textRect;

    GraphicsLayer *figureLayer;

    void fillFigureRenderingHints(FigureRenderingHints *hints);
    void clear();

protected:
    void mousePressEvent(QMouseEvent *event) override;
    void contextMenuEvent(QContextMenuEvent * event) override;

signals:
    void click(QMouseEvent*);
    void contextMenuRequested(QContextMenuEvent*);

public:
    CanvasViewer();
    ~CanvasViewer();

    void setObject(cCanvas *obj);
    std::vector<cObject *> getObjectsAt(const QPoint &pos);

    CanvasRenderer *getCanvasRenderer() { return canvasRenderer; }

    void redraw();
    void refresh();
};

} // namespace qtenv
} // namespace omnetpp

#endif // CANVASVIEWER_H