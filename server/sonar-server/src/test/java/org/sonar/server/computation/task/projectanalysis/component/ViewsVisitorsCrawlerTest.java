/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.computation.task.projectanalysis.component;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.sonar.server.computation.task.projectanalysis.component.Component.Type.PROJECT_VIEW;
import static org.sonar.server.computation.task.projectanalysis.component.Component.Type.SUBVIEW;
import static org.sonar.server.computation.task.projectanalysis.component.Component.Type.VIEW;
import static org.sonar.server.computation.task.projectanalysis.component.ComponentVisitor.Order.POST_ORDER;
import static org.sonar.server.computation.task.projectanalysis.component.ComponentVisitor.Order.PRE_ORDER;

public class ViewsVisitorsCrawlerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final Component PROJECT_VIEW_5 = component(PROJECT_VIEW, 5);
  private static final Component SUBVIEW_4 = component(SUBVIEW, 4, PROJECT_VIEW_5);
  private static final Component SUBVIEW_3 = component(SUBVIEW, 3, SUBVIEW_4);
  private static final Component SUBVIEW_2 = component(SUBVIEW, 2, SUBVIEW_3);
  private static final Component COMPONENT_TREE = component(VIEW, 1, SUBVIEW_2);

  private final TypeAwareVisitor spyPreOrderTypeAwareVisitor = spy(new TestTypeAwareVisitor(CrawlerDepthLimit.PROJECT_VIEW, PRE_ORDER));
  private final TypeAwareVisitor spyPostOrderTypeAwareVisitor = spy(new TestTypeAwareVisitor(CrawlerDepthLimit.PROJECT_VIEW, POST_ORDER));
  private final TestPathAwareVisitor spyPathAwareVisitor = spy(new TestPathAwareVisitor(CrawlerDepthLimit.PROJECT_VIEW, POST_ORDER));

  @Test
  public void execute_each_visitor_on_each_level() throws Exception {
    InOrder inOrder = inOrder(spyPostOrderTypeAwareVisitor, spyPathAwareVisitor);
    VisitorsCrawler underTest = new VisitorsCrawler(Arrays.asList(spyPostOrderTypeAwareVisitor, spyPathAwareVisitor));
    underTest.visit(COMPONENT_TREE);

    inOrder.verify(spyPostOrderTypeAwareVisitor).visitAny(PROJECT_VIEW_5);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitProjectView(PROJECT_VIEW_5);
    inOrder.verify(spyPathAwareVisitor).visitAny(eq(PROJECT_VIEW_5), any(PathAwareVisitor.Path.class));
    inOrder.verify(spyPathAwareVisitor).visitProjectView(eq(PROJECT_VIEW_5), any(PathAwareVisitor.Path.class));

    inOrder.verify(spyPostOrderTypeAwareVisitor).visitAny(SUBVIEW_4);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitSubView(SUBVIEW_4);
    inOrder.verify(spyPathAwareVisitor).visitAny(eq(SUBVIEW_4), any(PathAwareVisitor.Path.class));
    inOrder.verify(spyPathAwareVisitor).visitSubView(eq(SUBVIEW_4), any(PathAwareVisitor.Path.class));

    inOrder.verify(spyPostOrderTypeAwareVisitor).visitAny(SUBVIEW_3);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitSubView(SUBVIEW_3);
    inOrder.verify(spyPathAwareVisitor).visitAny(eq(SUBVIEW_3), any(PathAwareVisitor.Path.class));
    inOrder.verify(spyPathAwareVisitor).visitSubView(eq(SUBVIEW_3), any(PathAwareVisitor.Path.class));

    inOrder.verify(spyPostOrderTypeAwareVisitor).visitAny(SUBVIEW_2);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitSubView(SUBVIEW_2);
    inOrder.verify(spyPathAwareVisitor).visitAny(eq(SUBVIEW_2), any(PathAwareVisitor.Path.class));
    inOrder.verify(spyPathAwareVisitor).visitSubView(eq(SUBVIEW_2), any(PathAwareVisitor.Path.class));

    inOrder.verify(spyPostOrderTypeAwareVisitor).visitAny(COMPONENT_TREE);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitView(COMPONENT_TREE);
    inOrder.verify(spyPathAwareVisitor).visitAny(eq(COMPONENT_TREE), any(PathAwareVisitor.Path.class));
    inOrder.verify(spyPathAwareVisitor).visitView(eq(COMPONENT_TREE), any(PathAwareVisitor.Path.class));
  }

  @Test
  public void execute_pre_visitor_before_post_visitor() throws Exception {
    InOrder inOrder = inOrder(spyPreOrderTypeAwareVisitor, spyPostOrderTypeAwareVisitor);
    VisitorsCrawler underTest = new VisitorsCrawler(Arrays.<ComponentVisitor>asList(spyPreOrderTypeAwareVisitor, spyPostOrderTypeAwareVisitor));
    underTest.visit(COMPONENT_TREE);

    inOrder.verify(spyPreOrderTypeAwareVisitor).visitView(COMPONENT_TREE);
    inOrder.verify(spyPreOrderTypeAwareVisitor).visitSubView(SUBVIEW_2);
    inOrder.verify(spyPreOrderTypeAwareVisitor).visitSubView(SUBVIEW_3);
    inOrder.verify(spyPreOrderTypeAwareVisitor).visitSubView(SUBVIEW_4);
    inOrder.verify(spyPreOrderTypeAwareVisitor).visitProjectView(PROJECT_VIEW_5);

    inOrder.verify(spyPostOrderTypeAwareVisitor).visitProjectView(PROJECT_VIEW_5);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitSubView(SUBVIEW_4);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitSubView(SUBVIEW_3);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitSubView(SUBVIEW_2);
    inOrder.verify(spyPostOrderTypeAwareVisitor).visitView(COMPONENT_TREE);
  }

  @Test
  public void fail_with_IAE_when_visitor_is_not_path_aware_or_type_aware() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Only TypeAwareVisitor and PathAwareVisitor can be used");

    ComponentVisitor componentVisitor = new ComponentVisitor() {
      @Override
      public Order getOrder() {
        return PRE_ORDER;
      }

      @Override
      public CrawlerDepthLimit getMaxDepth() {
        return CrawlerDepthLimit.PROJECT_VIEW;
      }
    };
    new VisitorsCrawler(Arrays.asList(componentVisitor));
  }

  private static Component component(final Component.Type type, final int ref, final Component... children) {
    return ViewsComponent.builder(type, ref).addChildren(children).build();
  }

  private static class TestTypeAwareVisitor extends TypeAwareVisitorAdapter {

    public TestTypeAwareVisitor(CrawlerDepthLimit maxDepth, Order order) {
      super(maxDepth, order);
    }
  }

  private static class TestPathAwareVisitor extends PathAwareVisitorAdapter<Integer> {

    public TestPathAwareVisitor(CrawlerDepthLimit maxDepth, Order order) {
      super(maxDepth, order, new SimpleStackElementFactory<Integer>() {
        @Override
        public Integer createForAny(Component component) {
          return Integer.valueOf(component.getKey());
        }
      });
    }
  }

}
