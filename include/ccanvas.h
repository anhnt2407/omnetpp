//==========================================================================
//   CCANVAS.H  -  header for
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2008 Andras Varga
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __CCANVAS_H
#define __CCANVAS_H

#include "cownedobject.h"

NAMESPACE_BEGIN

class cCanvas;
class cProperty;
class cProperties;

#define OMNETPP_CANVAS_VERSION  0x20140702  //XXX identifies canvas code version until API stabilizes

//TODO color names, moveBelow/moveAbove; clean up submoduleLayer stuff: use @figure[submodules](type=group) instead of explicitly adding one!; childZ=...

/**
 * TODO document.
 * Note: dup() makes shallow copy (doesn't copy child figures)
 */
class SIM_API cFigure : public cOwnedObject
{
    public:
        struct Point {
            double x, y;
            Point() : x(0), y(0) {}
            Point(double x, double y) : x(x), y(y) {}
        };

        struct Color {
            uint8_t red, green, blue; // later: alpha
            Color() : red(0), green(0), blue(0) {}
            Color(uint8_t red, uint8_t green, uint8_t blue) : red(red), green(green), blue(blue) {}
        };

        static const Color BLACK;
        static const Color WHITE;
        static const Color GREY;
        static const Color RED;
        static const Color GREEN;
        static const Color BLUE;
        static const Color YELLOW;
        static const Color CYAN;
        static const Color MAGENTA;

        struct Font {
            std::string typeface;
            int pointSize;
            uint8_t style;
            Font() : pointSize(0), style(FONT_NONE) {}
            Font(std::string typeface, int pointSize, uint8_t style=FONT_NONE) : typeface(typeface), pointSize(pointSize), style(style) {}
        };

        enum FontStyle { FONT_NONE=0, FONT_BOLD=1, FONT_ITALIC=2, FONT_UNDERLINE=4 };
        enum LineStyle { LINE_SOLID, LINE_DOTTED, LINE_DASHED };
        enum CapStyle { CAP_BUTT, CAP_SQUARE, CAP_ROUND };
        enum JoinStyle { JOIN_BEVEL, JOIN_MITER, JOIN_ROUND };
        enum ArrowHead { ARROW_NONE, ARROW_SIMPLE, ARROW_TRIANGLE, ARROW_BARBED };
        enum Anchor {ANCHOR_CENTER, ANCHOR_N, ANCHOR_E, ANCHOR_S, ANCHOR_W, ANCHOR_NW, ANCHOR_NE, ANCHOR_SE, ANCHOR_SW };
        enum Alignment { ALIGN_LEFT, ALIGN_RIGHT, ALIGN_CENTER }; // note: JUSTIFY is not supported by Tk

        // internal:
        enum {CHANGE_VISUAL=1, CHANGE_GEOMETRY=2, CHANGE_STRUCTURAL=4};

    private:
        static int lastId;
        int id;
        bool visible; // treated as structural change, for simpler handing
        std::vector<cFigure*> children;
        opp_string tags; //TODO stringpool
        uint64 tagBits;  // bit-to-tagname mapping is stored in cCanvas. Note: change to std::bitset if 64 tags are not enough

        int8_t localChange;
        int8_t treeChange;

    protected:
        virtual cFigure *getRootFigure();
        virtual void doVisualChange() {doChange(CHANGE_VISUAL);}
        virtual void doGeometryChange() {doChange(CHANGE_GEOMETRY);}
        virtual void doStructuralChange() {doChange(CHANGE_STRUCTURAL);}
        virtual void doChange(int flags);
        static Point parsePoint(cProperty *property, const char *key, int index);
        static std::vector<Point> parsePoints(cProperty *property, const char *key);
        static void parseBoundingBox(cProperty *property, Point& p1, Point& p2);
        static Font parseFont(cProperty *property, const char *key);
        static bool parseBool(const char *s);
        static Color parseColor(const char *s);
        static LineStyle parseLineStyle(const char *s);
        static CapStyle parseCapStyle(const char *s);
        static JoinStyle parseJoinStyle(const char *s);
        static ArrowHead parseArrowHead(const char *s);
        static Anchor parseAnchor(const char *s);
        static Alignment parseAlignment(const char *s);

    public:
        // internal:
        int getLocalChangeFlags() const {return localChange;}
        int getTreeChangeFlags() const {return treeChange;}
        void clearChangeFlags();
        void insertChild(cFigure *figure, std::map<cFigure*,double>& orderMap);
        void refreshTagBits();
        void refreshTagBitsRec();
        int64 getTagBits() const {return tagBits;}
        void setTagBits(uint64 tagBits) {this->tagBits = tagBits;}

    private:
        void copy(const cFigure& other);

    public:
        cFigure(const char *name=NULL) : cOwnedObject(name), id(++lastId), visible(true), tagBits(0), localChange(0), treeChange(0) {}
        cFigure(const cFigure& other) : cOwnedObject(other) {copy(other);}
        cFigure& operator=(const cFigure& other);
        virtual void forEachChild(cVisitor *v);
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual cFigure *dupTree();
        virtual const char *getClassNameForRenderer() const {return getClassName();} // denotes renderer of which figure class to use; override if you want to subclass a figure while reusing renderer of the base class

        int getId() const {return id;}
        virtual const Point& getLocation() const = 0;
        virtual void translate(double x, double y) = 0;
        virtual cCanvas *getCanvas();
        virtual bool isVisible() const {return visible;}
        virtual void setVisible(bool visible) {this->visible = visible; doStructuralChange();}
        virtual const char *getTags() const {return tags.c_str();}
        virtual void setTags(const char *tags) {this->tags = tags; refreshTagBits();}

        virtual void addChildFigure(cFigure *figure);
        virtual void addChildFigure(cFigure *figure, int pos);
        virtual cFigure *removeChildFigure(int pos);
        virtual cFigure *removeChildFigure(cFigure *figure);
        virtual int findChildFigure(const char *name);
        virtual int findChildFigure(cFigure *figure);
        virtual bool hasChildFigures() const {return !children.empty();}
        virtual int getNumChildFigures() const {return children.size();}
        virtual cFigure *getChildFigure(int pos);
        virtual cFigure *getChildFigure(const char *name);
        virtual cFigure *getParentFigure()  {return dynamic_cast<cFigure*>(getOwner());}
        virtual cFigure *findFigureByName(const char *name);  // searches recursively
        virtual cFigure *getFigureByPath(const char *path);  //NOTE: path has similar syntax to cModule::getModuleByPath()
        virtual void raiseAbove(cFigure *figure);
        virtual void lowerBelow(cFigure *figure);
};

class SIM_API cGroupFigure : public cFigure
{
    private:
        Point loc;
    private:
        void copy(const cGroupFigure& other);
    public:
        cGroupFigure(const char *name=NULL) : cFigure(name) {}
        cGroupFigure(const cGroupFigure& other) : cFigure(other) {copy(other);}
        cGroupFigure& operator=(const cGroupFigure& other);
        virtual cGroupFigure *dup() const  {return new cGroupFigure(*this);}
        virtual std::string info() const;
        virtual const char *getClassNameForRenderer() const {return "";} // non-visual figure
        virtual void translate(double x, double y);
        virtual const Point& getLocation() const  {return loc;}
        virtual void setLocation(const Point& loc)  {this->loc = loc; doGeometryChange();}
};

class SIM_API cAbstractLineFigure : public cFigure
{
    private:
        Color lineColor;
        LineStyle lineStyle;
        int lineWidth;
        ArrowHead startArrowHead, endArrowHead;
    private:
        void copy(const cAbstractLineFigure& other);
    public:
        cAbstractLineFigure(const char *name=NULL) : cFigure(name), lineColor(BLACK), lineStyle(LINE_SOLID), lineWidth(1), startArrowHead(ARROW_NONE), endArrowHead(ARROW_NONE) {}
        cAbstractLineFigure(const cAbstractLineFigure& other) : cFigure(other) {copy(other);}
        cAbstractLineFigure& operator=(const cAbstractLineFigure& other);
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Color& getLineColor() const  {return lineColor;}
        virtual void setLineColor(const Color& lineColor)  {this->lineColor = lineColor; doVisualChange();}
        virtual int getLineWidth() const  {return lineWidth;}
        virtual void setLineWidth(int lineWidth)  {this->lineWidth = lineWidth; doVisualChange();}
        virtual LineStyle getLineStyle() const  {return lineStyle;}
        virtual void setLineStyle(LineStyle lineStyle)  {this->lineStyle = lineStyle; doVisualChange();}
        virtual ArrowHead getStartArrowHead() const  {return startArrowHead;}
        virtual void setStartArrowHead(ArrowHead startArrowHead)  {this->startArrowHead = startArrowHead; doVisualChange();}
        virtual ArrowHead getEndArrowHead() const  {return endArrowHead;}
        virtual void setEndArrowHead(ArrowHead endArrowHead)  {this->endArrowHead = endArrowHead; doVisualChange();}
};

// Tkenv limitation: there is one arrowhead type for the whole line (cannot be different at the two ends)
class SIM_API cLineFigure : public cAbstractLineFigure
{
    private:
        Point start, end;
        CapStyle capStyle;  //TODO maybe into AbstractLineStyle?
    private:
        void copy(const cLineFigure& other);
    public:
        cLineFigure(const char *name=NULL) : cAbstractLineFigure(name), capStyle(CAP_BUTT) {}
        cLineFigure(const cLineFigure& other) : cAbstractLineFigure(other) {copy(other);}
        cLineFigure& operator=(const cLineFigure& other);
        virtual cLineFigure *dup() const  {return new cLineFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {return start;}
        virtual void translate(double x, double y);
        virtual const Point& getStart() const  {return start;}
        virtual void setStart(const Point& start)  {this->start = start; doGeometryChange();}
        virtual const Point& getEnd() const  {return end;}
        virtual void setEnd(const Point& end)  {this->end = end; doGeometryChange();}
        virtual CapStyle getCapStyle() const {return capStyle;}
        virtual void setCapStyle(CapStyle capStyle) {this->capStyle = capStyle; doVisualChange();}
};

// Note: Tkenv limitation: capStyle not supported; arrowheads not supported
class SIM_API cArcFigure : public cAbstractLineFigure
{
    private:
        Point p1, p2; // bounding box of the oval that arc is part of
        double startAngle, endAngle; // in degrees, CCW, 0=east
    private:
        void copy(const cArcFigure& other);
    public:
        cArcFigure(const char *name=NULL) : cAbstractLineFigure(name), startAngle(0), endAngle(0) {}
        cArcFigure(const cArcFigure& other) : cAbstractLineFigure(other) {copy(other);}
        cArcFigure& operator=(const cArcFigure& other);
        virtual cArcFigure *dup() const  {return new cArcFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {return p1;}
        virtual void translate(double x, double y);
        virtual const Point& getP1() const  {return p1;}
        virtual void setP1(const Point& p1)  {this->p1 = p1; doGeometryChange();}
        virtual const Point& getP2() const  {return p2;}
        virtual void setP2(const Point& p2)  {this->p2 = p2; doGeometryChange();}
        virtual double getStartAngle() const {return startAngle;}
        virtual void setStartAngle(double startAngle) {this->startAngle = startAngle; doVisualChange();}
        virtual double getEndAngle() const {return endAngle;}
        virtual void setEndAngle(double endAngle) {this->endAngle = endAngle; doVisualChange();}
};

// Tkenv limitation: there is one arrowhead type for the whole line (cannot be different at the two ends)
class SIM_API cPolylineFigure : public cAbstractLineFigure
{
    private:
        std::vector<Point> points;
        bool smooth;
        CapStyle capStyle;
        JoinStyle joinStyle;
    private:
        void copy(const cPolylineFigure& other);
    public:
        cPolylineFigure(const char *name=NULL) : cAbstractLineFigure(name), smooth(false), capStyle(CAP_BUTT), joinStyle(JOIN_MITER) {}
        cPolylineFigure(const cPolylineFigure& other) : cAbstractLineFigure(other) {copy(other);}
        cPolylineFigure& operator=(const cPolylineFigure& other);
        virtual cPolylineFigure *dup() const  {return new cPolylineFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {static Point dummy; return points.empty() ? dummy : points[0];}
        virtual void translate(double x, double y);
        virtual const std::vector<Point>& getPoints() const  {return points;}
        virtual void setPoints(const std::vector<Point>& points) {this->points = points; doGeometryChange();}
        virtual int getNumPoints() const {return points.size();}
        virtual const Point& getPoint(int i) const {return points[i];}
        virtual bool getSmooth() const {return smooth;}
        virtual void setSmooth(bool smooth) {this->smooth = smooth; doVisualChange();}
        virtual CapStyle getCapStyle() const {return capStyle;}
        virtual void setCapStyle(CapStyle capStyle) {this->capStyle = capStyle; doVisualChange();}
        virtual JoinStyle getJoinStyle() const {return joinStyle;}
        virtual void setJoinStyle(JoinStyle joinStyle) {this->joinStyle = joinStyle; doVisualChange();}
};

class SIM_API cAbstractShapeFigure : public cFigure
{
    private:
        bool outlined;
        bool filled;
        Color lineColor;
        Color fillColor;
        LineStyle lineStyle;
        int lineWidth;
    private:
        void copy(const cAbstractShapeFigure& other);
    public:
        cAbstractShapeFigure(const char *name=NULL) : cFigure(name), outlined(true), filled(false), lineColor(BLACK), fillColor(BLUE), lineStyle(LINE_SOLID), lineWidth(1) {}
        cAbstractShapeFigure(const cAbstractShapeFigure& other) : cFigure(other) {copy(other);}
        cAbstractShapeFigure& operator=(const cAbstractShapeFigure& other);
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual bool isFilled() const  {return filled;}
        virtual void setFilled(bool filled)  {this->filled = filled; doVisualChange();}
        virtual bool isOutlined() const  {return outlined;}
        virtual void setOutlined(bool outlined)  {this->outlined = outlined; doVisualChange();}
        virtual const Color& getLineColor() const  {return lineColor;}
        virtual void setLineColor(const Color& lineColor)  {this->lineColor = lineColor; doVisualChange();}
        virtual const Color& getFillColor() const  {return fillColor;}
        virtual void setFillColor(const Color& fillColor)  {this->fillColor = fillColor; doVisualChange();}
        virtual LineStyle getLineStyle() const  {return lineStyle;}
        virtual void setLineStyle(LineStyle lineStyle)  {this->lineStyle = lineStyle; doVisualChange();}
        virtual int getLineWidth() const  {return lineWidth;}
        virtual void setLineWidth(int lineWidth)  {this->lineWidth = lineWidth; doVisualChange();}
};

class SIM_API cRectangleFigure : public cAbstractShapeFigure
{
    private:
        Point p1, p2;
    private:
        void copy(const cRectangleFigure& other);
    public:
        cRectangleFigure(const char *name=NULL) : cAbstractShapeFigure(name) {}
        cRectangleFigure(const cRectangleFigure& other) : cAbstractShapeFigure(other) {copy(other);}
        cRectangleFigure& operator=(const cRectangleFigure& other);
        virtual cRectangleFigure *dup() const  {return new cRectangleFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {return p1;}
        virtual void translate(double x, double y);
        virtual const Point& getP1() const  {return p1;}
        virtual void setP1(const Point& p1)  {this->p1 = p1; doGeometryChange();}
        virtual const Point& getP2() const  {return p2;}
        virtual void setP2(const Point& p2)  {this->p2 = p2; doGeometryChange();}
};

class SIM_API cOvalFigure : public cAbstractShapeFigure
{
    private:
        Point p1, p2; // bounding box
    private:
        void copy(const cOvalFigure& other);
    public:
        cOvalFigure(const char *name=NULL) : cAbstractShapeFigure(name) {}
        cOvalFigure(const cOvalFigure& other) : cAbstractShapeFigure(other) {copy(other);}
        cOvalFigure& operator=(const cOvalFigure& other);
        virtual cOvalFigure *dup() const  {return new cOvalFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {return p1;}
        virtual void translate(double x, double y);
        virtual const Point& getP1() const  {return p1;}
        virtual void setP1(const Point& p1)  {this->p1 = p1; doGeometryChange();}
        virtual const Point& getP2() const  {return p2;}
        virtual void setP2(const Point& p2)  {this->p2 = p2; doGeometryChange();}
};

class SIM_API cPieSliceFigure : public cAbstractShapeFigure
{
    private:
        Point p1, p2; // bounding box of oval
        double startAngle, endAngle; // in degrees, CCW, 0=east
    private:
        void copy(const cPieSliceFigure& other);
    public:
        cPieSliceFigure(const char *name=NULL) : cAbstractShapeFigure(name), startAngle(0), endAngle(45) {}
        cPieSliceFigure(const cPieSliceFigure& other) : cAbstractShapeFigure(other) {copy(other);}
        cPieSliceFigure& operator=(const cPieSliceFigure& other);
        virtual cPieSliceFigure *dup() const  {return new cPieSliceFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {return p1;}
        virtual void translate(double x, double y);
        virtual const Point& getP1() const  {return p1;}
        virtual void setP1(const Point& p1)  {this->p1 = p1; doGeometryChange();}
        virtual const Point& getP2() const  {return p2;}
        virtual void setP2(const Point& p2)  {this->p2 = p2; doGeometryChange();}
        virtual double getStartAngle() const {return startAngle;}
        virtual void setStartAngle(double startAngle) {this->startAngle = startAngle; doVisualChange();}
        virtual double getEndAngle() const {return endAngle;}
        virtual void setEndAngle(double endAngle) {this->endAngle = endAngle; doVisualChange();}
};

class SIM_API cPolygonFigure : public cAbstractShapeFigure
{
    private:
        std::vector<Point> points;
        bool smooth;
        JoinStyle joinStyle;
    private:
        void copy(const cPolygonFigure& other);
    public:
        cPolygonFigure(const char *name=NULL) : cAbstractShapeFigure(name), smooth(false), joinStyle(JOIN_MITER) {}
        cPolygonFigure(const cPolygonFigure& other) : cAbstractShapeFigure(other) {copy(other);}
        cPolygonFigure& operator=(const cPolygonFigure& other);
        virtual cPolygonFigure *dup() const  {return new cPolygonFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {static Point dummy; return points.empty() ? dummy : points[0];}
        virtual void translate(double x, double y);
        virtual const std::vector<Point>& getPoints() const  {return points;}
        virtual void setPoints(const std::vector<Point>& points) {this->points = points; doGeometryChange();}
        virtual int getNumPoints() const {return points.size();}
        virtual const Point& getPoint(int i) const {return points[i];}
        virtual bool getSmooth() const {return smooth;}
        virtual void setSmooth(bool smooth) {this->smooth = smooth; doVisualChange();}
        virtual JoinStyle getJoinStyle() const {return joinStyle;}
        virtual void setJoinStyle(JoinStyle joinStyle) {this->joinStyle = joinStyle; doVisualChange();}
};

class SIM_API cTextFigure : public cFigure
{
    private:
        Point pos;
        Color color;
        Font font;
        std::string text;
        Anchor anchor;
        Alignment alignment;
    private:
        void copy(const cTextFigure& other);
    public:
        cTextFigure(const char *name=NULL) : cFigure(name), color(BLACK), anchor(ANCHOR_NW), alignment(ALIGN_LEFT) {}
        cTextFigure(const cTextFigure& other) : cFigure(other) {copy(other);}
        cTextFigure& operator=(const cTextFigure& other);
        virtual cTextFigure *dup() const  {return new cTextFigure(*this);}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {return pos;}
        virtual void translate(double x, double y);
        virtual const Point& getPos() const  {return pos;}  //TODO naming?
        virtual void setPos(const Point& pos)  {this->pos = pos; doGeometryChange();}
        virtual const Color& getColor() const  {return color;}
        virtual void setColor(const Color& color)  {this->color = color; doVisualChange();}
        virtual const Font& getFont() const  {return font;}
        virtual void setFont(Font font)  {this->font = font; doVisualChange();}
        virtual const char *getText() const  {return text.c_str();}
        virtual void setText(const char *text)  {this->text = text; doVisualChange();}
        virtual Anchor getAnchor() const  {return anchor;}
        virtual void setAnchor(Anchor anchor)  {this->anchor = anchor; doVisualChange();}
        virtual Alignment getAlignment() const  {return alignment;}
        virtual void setAlignment(Alignment alignment)  {this->alignment = alignment; doVisualChange();}
};

//Note: currently not implemented in Tkenv
class SIM_API cImageFigure : public cFigure
{
    private:
        Point pos;
        std::string imageName;
        Color color;
        int colorization;
        Anchor anchor;
    private:
        void copy(const cImageFigure& other);
    public:
        cImageFigure(const cImageFigure& other) : cFigure(other) {copy(other);}
        cImageFigure& operator=(const cImageFigure& other);
        virtual cImageFigure *dup() const  {return new cImageFigure(*this);}
        cImageFigure(const char *name=NULL) : cFigure(name), color(BLUE), colorization(0), anchor(ANCHOR_CENTER) {}
        virtual std::string info() const;
        virtual void parse(cProperty *property);
        virtual const Point& getLocation() const  {return pos;}
        virtual void translate(double x, double y);
        virtual const Point& getPos() const  {return pos;}
        virtual void setPos(const Point& pos)  {this->pos = pos; doGeometryChange();}
        virtual const char *getImageName() const  {return imageName.c_str();}
        virtual void setImageName(const char *imageName)  {this->imageName = imageName; doVisualChange();}
        virtual const Color& getColor() const  {return color;}
        virtual void setColor(const Color& color)  {this->color = color; doVisualChange();}
        virtual int getColorization() const  {return colorization;}
        virtual void setColorization(int colorization)  {this->colorization = colorization; doVisualChange();}
        virtual Anchor getAnchor() const  {return anchor;}
        virtual void setAnchor(Anchor anchor)  {this->anchor = anchor; doVisualChange();}
};


/**
 * TODO document
 * Note: dup() makes deep copy (figure tree too)
 */
class SIM_API cCanvas : public cOwnedObject
{
    private:
        cFigure *rootFigure;
        std::map<std::string,int> tagBitIndex;  // tag-to-bitindex
    protected:
        virtual void parseFigure(cProperty *property, std::map<cFigure*,double>& orderMap);
        virtual cFigure *createFigure(const char *type);
    public:
        // internal:
        static bool containsCanvasItems(cProperties *properties);
        virtual void addFiguresFrom(cProperties *properties);
        virtual uint64 parseTags(const char *s);
    private:
        void copy(const cCanvas& other);
    public:
        cCanvas(const char *name = NULL);
        cCanvas(const cCanvas& other) : cOwnedObject(other) {copy(other);}
        cCanvas& operator=(const cCanvas& other);
        virtual cCanvas *dup() const  {return new cCanvas(*this);}
        virtual ~cCanvas();
        virtual void forEachChild(cVisitor *v);
        virtual std::string info() const;

        virtual cFigure *getRootFigure() const {return rootFigure;}
        virtual void addFigure(cFigure *figure) {rootFigure->addChildFigure(figure);}
        virtual void addFigure(cFigure *figure, int pos) {rootFigure->addChildFigure(figure, pos);}
        virtual cFigure *removeFigure(int pos) {return rootFigure->removeChildFigure(pos);}
        virtual cFigure *removeFigure(cFigure *figure) {return rootFigure->removeChildFigure(figure);}
        virtual int findFigure(const char *name) {return rootFigure->findChildFigure(name);}
        virtual int findFigure(cFigure *figure) {return rootFigure->findChildFigure(figure);}
        virtual bool hasFigures() const {return rootFigure->hasChildFigures();}
        virtual int getNumFigures() const {return rootFigure->getNumChildFigures();} // TODO misnomer: returns num of *child* figures, not the total!!!
        virtual cFigure *getFigure(int pos) {return rootFigure->getChildFigure(pos);}
        virtual cFigure *getFigure(const char *name) {return rootFigure->getChildFigure(name);}
        virtual cFigure *findFigureByName(const char *name) {return rootFigure->findFigureByName(name);}
        virtual cFigure *getFigureByPath(const char *path) {return rootFigure->getFigureByPath(path);}
        virtual cFigure *getSubmodulesLayer() const; // may return NULL (extra canvases don't have submodules)
        virtual std::vector<std::string> getAllTags() const;
};

NAMESPACE_END


#endif

