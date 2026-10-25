package xyz.ssander.rings.client;

/** Marks the PlayerModel that OwnHeadLayer uses to draw just the head (see PlayerModelMixin). */
public interface HeadOnlyModel {
	void rings$setHeadOnly(boolean headOnly);
}
